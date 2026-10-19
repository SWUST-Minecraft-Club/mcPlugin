package online.yudream.minecraft.bukkit.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.api.HttpResult;
import online.yudream.minecraft.bukkit.api.YudreamApiClient;
import online.yudream.minecraft.bukkit.config.YudreamConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportQueue {
   private final JavaPlugin plugin;
   private final YudreamApiClient client;
   private final YudreamConfig config;
   private final ReportGate gate;
   private final PendingReportJournal journal;
   private final BlockingQueue<ReportTask> queue;
   private final ExecutorService executor;
   private volatile boolean running;
   private long nextReplayAttemptAt;

   public ReportQueue(JavaPlugin plugin, YudreamApiClient client, YudreamConfig config) {
      this(plugin, client, config, (ReportGate)null, (PendingReportJournal)null);
   }

   public ReportQueue(JavaPlugin plugin, YudreamApiClient client, YudreamConfig config, ReportGate gate) {
      this(plugin, client, config, gate, (PendingReportJournal)null);
   }

   public ReportQueue(JavaPlugin plugin, YudreamApiClient client, YudreamConfig config, ReportGate gate,
                      PendingReportJournal journal) {
      this.running = true;
      this.plugin = plugin;
      this.client = client;
      this.config = config;
      this.gate = gate;
      this.journal = journal;
      this.queue = new ArrayBlockingQueue(config.getQueueCapacity());
      this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
         public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "YuDream-Minecraft-Reporter");
            thread.setDaemon(true);
            return thread;
         }
      });
      this.executor.execute(new Runnable() {
         public void run() {
            ReportQueue.this.workerLoop();
         }
      });
   }

   public void submit(PlayerEventType type, PlayerEventPayload payload) {
      // 会影响在线时长的事件先落盘：后端不可用 / 未配置 / 网络失败都不丢，后端恢复后
      // 由 worker 按原始时间戳按序补发（后端按 eventAt 结算，且 join / quit / AFK 幂等，不会重复计时）。
      if (this.journal != null && this.config.isEnabled() && isDurableEvent(type)) {
         this.journal.append(type, payload);
         if (this.config.isLogQueued()) {
            this.plugin.getLogger().info("Queued YuDream report (durable): type=" + type
                    + ", player=" + payload.getPlayerName() + ", pending=" + this.journal.size());
         }

         return;
      }

      if (this.canSubmit()) {
         ReportTask task = ReportQueue.ReportTask.event(type, payload);
         this.enqueue(task);
      }
   }

   private static boolean isDurableEvent(PlayerEventType type) {
      return type == PlayerEventType.JOIN || type == PlayerEventType.QUIT
              || type == PlayerEventType.AFK_START || type == PlayerEventType.AFK_END;
   }

   public void submitSnapshot(Collection<PlayerEventPayload> players, long observedAt) {
      if (this.canSubmit()) {
         this.enqueue(ReportQueue.ReportTask.snapshot(players, observedAt));
      }
   }

   private boolean canSubmit() {
      if (this.config.isEnabled() && this.config.isConfigured()) {
         return this.gate == null || this.gate.canReport();
      } else {
         return false;
      }
   }

   private void enqueue(ReportTask task) {
      if (this.queue.offer(task)) {
         if (this.config.isLogQueued()) {
            this.plugin.getLogger().info("Queued YuDream report: " + this.describe(task) + ", queueSize=" + this.queue.size());
         }

      } else {
         if (this.config.isLogFailures()) {
            this.plugin.getLogger().warning("YuDream report queue is full; dropped " + this.describe(task));
         }

      }
   }

   public int size() {
      return this.queue.size() + (this.journal == null ? 0 : this.journal.size());
   }

   public void shutdown(long timeoutMs) {
      this.running = false;
      this.executor.shutdown();

      try {
         if (!this.executor.awaitTermination(Math.max(timeoutMs, 1L), TimeUnit.MILLISECONDS)) {
            this.executor.shutdownNow();
         }
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
         this.executor.shutdownNow();
      }

   }

   private void workerLoop() {
      while(this.running || !this.queue.isEmpty()) {
         try {
            if (this.running && this.journal != null && this.canSubmit()
                    && System.currentTimeMillis() >= this.nextReplayAttemptAt) {
               PendingReportJournal.Pending pending = this.journal.peek();
               if (pending != null) {
                  this.replay(pending);
                  continue;
               }
            }

            ReportTask task = (ReportTask)this.queue.poll(500L, TimeUnit.MILLISECONDS);
            if (task != null) {
               this.sendWithRetry(task);
            }
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            return;
         } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING, "Unexpected YuDream report worker error", t);
         }
      }

   }

   /**
    * 补发一条落盘的待发上报。成功后从日志里删掉；失败则退避一段时间再试（后端恢复后自动追平）。
    */
   private void replay(PendingReportJournal.Pending pending) {
      long startedAt = System.currentTimeMillis();

      try {
         HttpResult result = this.client.report(pending.getType(), pending.toPayload());
         if (result.isSuccess()) {
            this.journal.remove(pending.getId());
            this.nextReplayAttemptAt = 0L;
            if (this.config.isLogSuccess()) {
               this.plugin.getLogger().info("YuDream pending report replayed: type=" + pending.getType()
                       + ", player=" + pending.getPlayerName() + ", eventAt=" + pending.getEventAt()
                       + ", http=" + result.getStatusCode() + ", pendingLeft=" + this.journal.size());
            }

            return;
         }

         this.backOffReplay(startedAt, "http=" + result.getStatusCode());
      } catch (Exception e) {
         this.backOffReplay(startedAt, "error=" + e.getMessage());
      }
   }

   /** 补发失败后退避 30 秒再试，避免后端长时间不可用时刷屏。 */
   private void backOffReplay(long startedAt, String detail) {
      this.nextReplayAttemptAt = System.currentTimeMillis() + 30000L;
      if (this.config.isLogFailures()) {
         this.plugin.getLogger().warning("YuDream pending report replay failed, will retry in 30s: " + detail
                 + ", pending=" + this.journal.size() + ", elapsedMs=" + (System.currentTimeMillis() - startedAt));
      }
   }

   private void sendWithRetry(ReportTask task) throws InterruptedException {
      int attempts = this.config.getRetryAttempts();

      for(int attempt = 1; attempt <= attempts; ++attempt) {
         long startedAt = System.currentTimeMillis();

         try {
            if (this.config.isLogAttempts()) {
               this.plugin.getLogger().info("Sending YuDream report: " + this.describe(task) + ", attempt=" + attempt + "/" + attempts);
            }

            HttpResult result = task.snapshot ? this.client.snapshot(task.players, task.observedAt) : this.client.report(task.type, task.payload);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (result.isSuccess()) {
               if (this.config.isLogSuccess()) {
                  this.plugin.getLogger().info("YuDream report success: " + this.describe(task) + ", http=" + result.getStatusCode() + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs);
               }

               return;
            }

            if (this.config.isLogAttempts() && attempt < attempts) {
               this.plugin.getLogger().warning("YuDream report attempt failed: " + this.describe(task) + ", http=" + result.getStatusCode() + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs + ", response=" + trim(result.getBody()));
            }

            if (attempt == attempts && this.config.isLogFailures()) {
               this.plugin.getLogger().warning("YuDream report failed: " + this.describe(task) + ", http=" + result.getStatusCode() + ", attempts=" + attempts + ", elapsedMs=" + elapsedMs + ", response=" + trim(result.getBody()));
            }
         } catch (Exception var9) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (this.config.isLogAttempts() && attempt < attempts) {
               this.plugin.getLogger().warning("YuDream report attempt threw exception: " + this.describe(task) + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs + ", error=" + var9.getMessage());
            }

            if (attempt == attempts && this.config.isLogFailures()) {
               this.plugin.getLogger().log(Level.WARNING, "YuDream report failed: " + this.describe(task) + ", attempts=" + attempts + ", elapsedMs=" + elapsedMs, var9);
            }
         }

         if (attempt < attempts) {
            Thread.sleep(this.config.getRetryDelayMs());
         }
      }

   }

   private String describe(ReportTask task) {
      if (task.snapshot) {
         return "type=SNAPSHOT, endpoint=/players/snapshot, players=" + task.players.size() + (this.config.isLogPayload() ? ", observedAt=" + task.observedAt : "");
      } else {
         StringBuilder message = new StringBuilder();
         message.append("type=").append(task.type);
         message.append(", endpoint=/").append(task.type.getRemotePath());
         message.append(", player=").append(task.payload.getPlayerName());
         if (this.config.isLogPayload()) {
            message.append(", playerId=").append(task.payload.getPlayerId());
            message.append(", eventAt=").append(task.payload.getEventAt());
            if (task.payload.getContent() != null) {
               message.append(", content=").append(task.payload.getContent());
            }
         }

         return message.toString();
      }
   }

   private static String trim(String body) {
      if (body == null) {
         return "";
      } else {
         return body.length() <= 300 ? body : body.substring(0, 300) + "...";
      }
   }

   private static final class ReportTask {
      private final boolean snapshot;
      private final PlayerEventType type;
      private final PlayerEventPayload payload;
      private final List<PlayerEventPayload> players;
      private final long observedAt;

      private ReportTask(boolean snapshot, PlayerEventType type, PlayerEventPayload payload, Collection<PlayerEventPayload> players, long observedAt) {
         this.snapshot = snapshot;
         this.type = type;
         this.payload = payload;
         this.players = players == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(players));
         this.observedAt = observedAt;
      }

      private static ReportTask event(PlayerEventType type, PlayerEventPayload payload) {
         return new ReportTask(false, type, payload, (Collection)null, payload.getEventAt());
      }

      private static ReportTask snapshot(Collection<PlayerEventPayload> players, long observedAt) {
         return new ReportTask(true, (PlayerEventType)null, (PlayerEventPayload)null, players, observedAt);
      }
   }

   public interface ReportGate {
      boolean canReport();
   }
}
