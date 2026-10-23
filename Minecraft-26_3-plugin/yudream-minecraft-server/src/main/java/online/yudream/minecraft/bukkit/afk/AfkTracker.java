package online.yudream.minecraft.bukkit.afk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import online.yudream.minecraft.bukkit.config.YudreamConfig;
import online.yudream.minecraft.bukkit.report.PlayerEventType;
import online.yudream.minecraft.bukkit.report.ReportQueue;
import online.yudream.minecraft.bukkit.util.PlayerSnapshots;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AfkTracker {
   private final JavaPlugin plugin;
   private final ReportQueue reportQueue;
   private final YudreamConfig config;
   private final AfkListener listener;
   private final Map<UUID, State> states = new ConcurrentHashMap();
   private Object task;

   public AfkTracker(JavaPlugin plugin, ReportQueue reportQueue, YudreamConfig config) {
      this(plugin, reportQueue, config, (AfkListener)null);
   }

   public AfkTracker(JavaPlugin plugin, ReportQueue reportQueue, YudreamConfig config, AfkListener listener) {
      this.plugin = plugin;
      this.reportQueue = reportQueue;
      this.config = config;
      this.listener = listener;
   }

   public void start() {
      if (this.config.isAfkEnabled()) {
         this.task = FoliaSchedulerCompat.runGlobalRepeating(this.plugin, new Runnable() {
            public void run() {
               AfkTracker.this.checkAfk();
            }
         }, this.config.getAfkCheckIntervalTicks(), this.config.getAfkCheckIntervalTicks());
      }
   }

   public void stop() {
      if (this.task != null) {
         FoliaSchedulerCompat.cancel(this.task);
         this.task = null;
      }

      this.states.clear();
   }

   public void markOnline(Player player) {
      this.states.put(player.getUniqueId(), new State(System.currentTimeMillis(), false));
   }

   public void markOffline(Player player) {
      this.states.remove(player.getUniqueId());
   }

   public void markActive(Player player) {
      if (this.config.isAfkEnabled()) {
         UUID uuid = player.getUniqueId();
         long now = System.currentTimeMillis();
         State state = (State)this.states.get(uuid);
         if (state == null) {
            this.states.put(uuid, new State(now, false));
         } else {
            if (state.afk) {
               this.reportQueue.submit(PlayerEventType.AFK_END, PlayerSnapshots.from(player, now));
               if (this.listener != null) {
                  this.listener.onAfkEnd(player, now);
               }
            }

            this.states.put(uuid, new State(now, false));
         }
      }
   }

   public boolean isAfk(Player player) {
      State state = (State)this.states.get(player.getUniqueId());
      return state != null && state.afk;
   }

   private void checkAfk() {
      if (this.config.isAfkEnabled()) {
         long now = System.currentTimeMillis();

         for(Player player : this.plugin.getServer().getOnlinePlayers()) {
            State state = (State)this.states.get(player.getUniqueId());
            if (state == null) {
               this.states.put(player.getUniqueId(), new State(now, false));
            } else if (!state.afk && now - state.lastActiveAt >= this.config.getAfkTimeoutMs()) {
               this.states.put(player.getUniqueId(), new State(state.lastActiveAt, true));
               this.reportQueue.submit(PlayerEventType.AFK_START, PlayerSnapshots.from(player, now));
               if (this.listener != null) {
                  this.listener.onAfkStart(player, now);
               }
            }
         }

      }
   }

   /** AFK 状态发生翻转时的回调（本地时长台账用；上报路径不受影响）。 */
   public interface AfkListener {
      void onAfkStart(Player player, long eventAt);

      void onAfkEnd(Player player, long eventAt);
   }

   private static final class State {
      private final long lastActiveAt;
      private final boolean afk;

      private State(long lastActiveAt, boolean afk) {
         this.lastActiveAt = lastActiveAt;
         this.afk = afk;
      }
   }
}
