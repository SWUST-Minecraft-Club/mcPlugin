package online.yudream.minecraft.bridge.core.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import online.yudream.minecraft.bridge.core.config.BridgeSettings;
import online.yudream.minecraft.bridge.core.http.HttpResult;
import online.yudream.minecraft.bridge.core.http.YudreamApiClient;
import online.yudream.minecraft.bridge.core.log.LogSink;
import online.yudream.minecraft.bridge.core.model.PlayerEventPayload;
import online.yudream.minecraft.bridge.core.model.PlayerEventType;
import online.yudream.minecraft.bridge.core.model.SubServerRoster;

public final class ReportQueue {
   private static final long JOURNAL_FLUSH_INTERVAL_MS = 500L;
   private static final long POLL_INTERVAL_MS = 500L;
   private final LogSink log;
   private final BridgeSettings settings;
   private final YudreamApiClient client;
   private final ReportJournal journal;
   private final ReportGate gate;
   private final BlockingQueue<ReportTask> queue;
   private final Map<String, ReportTask> outstanding;
   private final Object outstandingLock;
   private final ExecutorService executor;
   private volatile boolean running;
   private volatile boolean dirty;
   private volatile long lastJournalSaveAt;

   public ReportQueue(LogSink log, BridgeSettings settings, YudreamApiClient client, ReportJournal journal) {
      this(log, settings, client, journal, (ReportGate)null);
   }

   public ReportQueue(LogSink log, BridgeSettings settings, YudreamApiClient client, ReportJournal journal, ReportGate gate) {
      this.outstanding = new LinkedHashMap();
      this.outstandingLock = new Object();
      this.running = true;
      this.log = log;
      this.settings = settings;
      this.client = client;
      this.journal = journal;
      this.gate = gate;
      List<ReportTask> recovered = (List<ReportTask>)(journal == null ? new ArrayList() : journal.load());
      int capacity = Math.max(settings.getQueueCapacity(), recovered.size() + 1);
      this.queue = new ArrayBlockingQueue(capacity);

      for(ReportTask task : recovered) {
         this.queue.offer(task);
         this.outstanding.put(task.id(), task);
      }

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
      if (this.canReport()) {
         this.enqueue(ReportTask.event(type, payload));
      }
   }

   public void submitSnapshot(Collection<PlayerEventPayload> players, long observedAt, String serverName) {
      if (this.canReport()) {
         this.enqueue(ReportTask.snapshot(players, observedAt, serverName));
      }
   }

   public void submitGroupedSnapshot(Collection<SubServerRoster> servers, long observedAt) {
      if (servers != null && !servers.isEmpty() && this.canReport()) {
         this.enqueue(ReportTask.groupedSnapshot(servers, observedAt));
      }
   }

   public int size() {
      return this.queue.size();
   }

   public int outstandingCount() {
      synchronized(this.outstandingLock) {
         return this.outstanding.size();
      }
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

      this.flushJournal();
   }

   private void enqueue(ReportTask task) {
      if (this.queue.offer(task)) {
         synchronized(this.outstandingLock) {
            this.outstanding.put(task.id(), task);
         }

         this.dirty = true;
         if (this.settings.isLogQueued()) {
            this.log.info("Queued YuDream report: " + this.describe(task) + ", queueSize=" + this.queue.size());
         }

      } else {
         if (this.settings.isLogFailures()) {
            this.log.warn("YuDream report queue is full; dropped " + this.describe(task));
         }

      }
   }

   private void workerLoop() {
      while(this.running || !this.queue.isEmpty()) {
         try {
            ReportTask task = (ReportTask)this.queue.poll(500L, TimeUnit.MILLISECONDS);
            if (task != null) {
               this.sendWithRetry(task);
               this.complete(task);
            }

            this.flushJournalIfDue();
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            return;
         } catch (Throwable t) {
            this.log.warn("Unexpected YuDream report worker error", t);
         }
      }

      this.flushJournal();
   }

   private void complete(ReportTask task) {
      synchronized(this.outstandingLock) {
         this.outstanding.remove(task.id());
      }

      this.dirty = true;
   }

   private void flushJournalIfDue() {
      if (this.journal != null && this.journal.isEnabled() && this.dirty) {
         if (this.queue.isEmpty() || System.currentTimeMillis() - this.lastJournalSaveAt >= 500L) {
            this.flushJournal();
         }

      }
   }

   private synchronized void flushJournal() {
      if (this.journal != null && this.journal.isEnabled()) {
         List<ReportTask> snapshot;
         synchronized(this.outstandingLock) {
            snapshot = new ArrayList(this.outstanding.values());
         }

         this.journal.save(snapshot);
         this.dirty = false;
         this.lastJournalSaveAt = System.currentTimeMillis();
      }
   }

   private void sendWithRetry(ReportTask task) throws InterruptedException {
      int attempts = this.settings.getRetryAttempts();

      for(int attempt = 1; attempt <= attempts; ++attempt) {
         long startedAt = System.currentTimeMillis();

         try {
            if (this.settings.isLogAttempts()) {
               this.log.info("Sending YuDream report: " + this.describe(task) + ", attempt=" + attempt + "/" + attempts);
            }

            HttpResult result;
            if (!task.snapshot()) {
               result = this.client.report(task.type(), task.payload());
            } else if (task.grouped()) {
               result = this.client.groupedSnapshot(task.servers(), task.observedAt());
            } else {
               result = this.client.snapshot(task.players(), task.observedAt(), task.serverName());
            }

            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (result.isSuccess()) {
               if (this.settings.isLogSuccess()) {
                  this.log.info("YuDream report success: " + this.describe(task) + ", http=" + result.statusCode() + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs);
               }

               return;
            }

            if (this.settings.isLogAttempts() && attempt < attempts) {
               this.log.warn("YuDream report attempt failed: " + this.describe(task) + ", http=" + result.statusCode() + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs + ", response=" + result.trimmedBody());
            }

            if (attempt == attempts && this.settings.isLogFailures()) {
               this.log.warn("YuDream report failed: " + this.describe(task) + ", http=" + result.statusCode() + ", attempts=" + attempts + ", elapsedMs=" + elapsedMs + ", response=" + result.trimmedBody());
            }
         } catch (Exception var9) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (this.settings.isLogAttempts() && attempt < attempts) {
               this.log.warn("YuDream report attempt threw exception: " + this.describe(task) + ", attempt=" + attempt + "/" + attempts + ", elapsedMs=" + elapsedMs + ", error=" + var9.getMessage());
            }

            if (attempt == attempts && this.settings.isLogFailures()) {
               this.log.warn("YuDream report failed: " + this.describe(task) + ", attempts=" + attempts + ", elapsedMs=" + elapsedMs, var9);
            }
         }

         if (attempt < attempts) {
            Thread.sleep(this.settings.getRetryDelayMs());
         }
      }

   }

   private String describe(ReportTask task) {
      return task.describe(this.settings.isLogPayload());
   }

   private boolean canReport() {
      if (this.settings.isEnabled() && this.settings.isConfigured()) {
         return this.gate == null || this.gate.canReport();
      } else {
         return false;
      }
   }

   public interface ReportGate {
      boolean canReport();
   }
}
