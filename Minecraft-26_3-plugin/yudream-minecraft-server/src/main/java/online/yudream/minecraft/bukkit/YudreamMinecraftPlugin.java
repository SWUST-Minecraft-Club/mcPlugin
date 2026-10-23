package online.yudream.minecraft.bukkit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.afk.AfkTracker;
import online.yudream.minecraft.bukkit.api.YudreamApiClient;
import online.yudream.minecraft.bukkit.bridge.BridgeMode;
import online.yudream.minecraft.bukkit.bridge.DownstreamSensor;
import online.yudream.minecraft.bukkit.bridge.InboundChatPoller;
import online.yudream.minecraft.bukkit.command.YudreamCommand;
import online.yudream.minecraft.bukkit.compat.AdvancementCompatibility;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import online.yudream.minecraft.bukkit.compat.PaperChatCompatibility;
import online.yudream.minecraft.bukkit.compat.ServerCompatibility;
import online.yudream.minecraft.bukkit.config.YudreamConfig;
import online.yudream.minecraft.bukkit.event.GameEventListener;
import online.yudream.minecraft.bukkit.event.PlayerActivityListener;
import online.yudream.minecraft.bukkit.playtime.PlaytimeLedger;
import online.yudream.minecraft.bukkit.playtime.PlaytimeSync;
import online.yudream.minecraft.bukkit.report.PendingReportJournal;
import online.yudream.minecraft.bukkit.report.PlayerEventPayload;
import online.yudream.minecraft.bukkit.report.PlayerEventType;
import online.yudream.minecraft.bukkit.report.ReportQueue;
import online.yudream.minecraft.bukkit.util.PlayerSnapshots;
import online.yudream.minecraft.bukkit.util.YamlPatcher;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class YudreamMinecraftPlugin extends JavaPlugin {
   private volatile YudreamConfig settings;
   private volatile YudreamApiClient apiClient;
   private volatile ReportQueue reportQueue;
   private volatile AfkTracker afkTracker;
   private PlaytimeLedger playtimeLedger;
   private PlaytimeSync playtimeSync;
   private volatile PendingReportJournal reportJournal;
   private volatile DownstreamSensor sensor;
   private volatile InboundChatPoller inboundPoller;
   private Object snapshotTask;
   private Object inboundPollTask;
   private Object playtimeSaveTask;
   private Object playtimeSyncTask;

   public void onEnable() {
      this.saveDefaultConfig();
      this.getLogger().info(ServerCompatibility.describe());
      this.playtimeLedger = new PlaytimeLedger(this);
      this.playtimeSync = new PlaytimeSync(this);
      this.reloadBridge();
      this.getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);
      this.getServer().getPluginManager().registerEvents(new GameEventListener(this), this);
      if (PaperChatCompatibility.register(this)) {
         this.getLogger().info("Paper AsyncChatEvent compatibility is active.");
      }

      if (AdvancementCompatibility.register(this)) {
         this.getLogger().info("Advancement event compatibility is active.");
      }

      PluginCommand command = this.getCommand("yudreammc");
      if (command != null) {
         YudreamCommand executor = new YudreamCommand(this);
         command.setExecutor(executor);
         command.setTabCompleter(executor);
      }

      if (this.settings.isSyncOnlineOnEnable()) {
         this.syncOnlinePlayers();
      }

      this.snapshotTask = FoliaSchedulerCompat.runGlobalRepeating(this, new Runnable() {
         public void run() {
            YudreamMinecraftPlugin.this.reportPlayerSnapshot();
         }
      }, 20L, 1200L);
      this.startPlaytimeSaveTask();
      this.startPlaytimeSyncTask();
   }

   public void onDisable() {
      if (this.snapshotTask != null) {
         FoliaSchedulerCompat.cancel(this.snapshotTask);
         this.snapshotTask = null;
      }

      this.stopPlaytimeSyncTask();
      this.stopPlaytimeSaveTask();
      if (this.playtimeLedger != null) {
         this.playtimeLedger.saveNow();
      }
      this.stopInboundPoller();
      if (this.sensor != null) {
         this.sensor.stop();
         this.sensor = null;
      }

      if (this.afkTracker != null) {
         this.afkTracker.stop();
      }

      if (this.settings != null && this.settings.isReportQuitOnDisable() && this.reportQueue != null && this.canReport()) {
         for(Player player : this.getServer().getOnlinePlayers()) {
            this.reportQueue.submit(PlayerEventType.QUIT, PlayerSnapshots.from(player));
         }
      }

      if (this.reportQueue != null && this.canReport()) {
         this.reportQueue.submitSnapshot(Collections.emptyList(), System.currentTimeMillis());
      }

      if (this.reportQueue != null) {
         this.reportQueue.shutdown(this.settings == null ? 5000L : this.settings.getFlushTimeoutMs());
      }

   }

   public synchronized void reloadBridge() {
      this.reloadConfig();
      this.settings = YudreamConfig.load(this);
      this.apiClient = new YudreamApiClient(this.settings);
      if (this.reportQueue != null) {
         this.reportQueue.shutdown(this.settings.getFlushTimeoutMs());
      }

      this.reportJournal = this.settings.getDurableQueue().isEnabled()
              ? new PendingReportJournal(this, this.settings.getDurableQueue()) : null;
      this.reportQueue = new ReportQueue(this, this.apiClient, this.settings, new ReportQueue.ReportGate() {
         public boolean canReport() {
            return YudreamMinecraftPlugin.this.canReport();
         }
      }, this.reportJournal);
      if (this.afkTracker != null) {
         this.afkTracker.stop();
      }

      this.afkTracker = new AfkTracker(this, this.reportQueue, this.settings, new AfkTracker.AfkListener() {
         public void onAfkStart(Player player, long eventAt) {
            YudreamMinecraftPlugin.this.recordAfk(true, player, eventAt);
         }

         public void onAfkEnd(Player player, long eventAt) {
            YudreamMinecraftPlugin.this.recordAfk(false, player, eventAt);
         }
      });
      this.afkTracker.start();
      this.stopPlaytimeSaveTask();
      this.startPlaytimeSaveTask();
      this.stopPlaytimeSyncTask();
      this.startPlaytimeSyncTask();
      if (this.sensor != null) {
         this.sensor.stop();
         this.sensor = null;
      }

      if (this.settings.isDownstream()) {
         this.sensor = new DownstreamSensor(this, this.settings.getDownstream());
         this.sensor.start();
      }

      this.startInboundPoller();
      this.warnAboutConfiguration();
   }

   private void startInboundPoller() {
      this.stopInboundPoller();
      if (this.settings.isEnabled() && this.settings.getChatBridge().isEnabled() && this.settings.getChatBridge().isPollInbound()) {
         if (this.inboundPoller == null) {
            this.inboundPoller = new InboundChatPoller(this);
         }

         this.inboundPollTask = FoliaSchedulerCompat.runAsyncRepeating(this, new Runnable() {
            public void run() {
               YudreamMinecraftPlugin.this.inboundPoller.poll();
            }
         }, 200L, this.settings.getChatBridge().getPollIntervalTicks());
         this.getLogger().info("群服互联入站已启动：server-id=" + this.settings.getServerId() + " -> " + this.settings.getBaseUrl() + "，实时推送优先，轮询兜底（间隔 " + this.settings.getChatBridge().getPollIntervalTicks() / 20L + " 秒），调试日志=" + (this.settings.getChatBridge().isDebug() ? "开" : "关") + "。server-id 必须与 Admin 侧配置了群服互联的服务器条目一致，否则只会收到心跳、永远收不到群消息。");
      } else {
         String reason = !this.settings.isEnabled() ? "bridge 总开关关闭" : (!this.settings.getChatBridge().isEnabled() ? "chat-bridge 未启用" : "poll-inbound 未启用");
         this.getLogger().info("群服互联入站未启动：" + reason + "。");
      }
   }

   private void stopInboundPoller() {
      if (this.inboundPollTask != null) {
         FoliaSchedulerCompat.cancel(this.inboundPollTask);
         this.inboundPollTask = null;
         this.getLogger().info("群服互联入站已停止。");
      }

   }

   private void warnAboutConfiguration() {
      if (!this.settings.isEnabled()) {
         this.getLogger().warning("YuDream bridge is disabled by config.");
      }

      if (this.settings.isDownstream()) {
         if (this.settings.getDownstream().isFallbackToApi() && !this.settings.isConfigured()) {
            this.getLogger().warning("downstream.fallback-to-api is enabled but base-url, server-id or api-key is missing, so there is nothing to fall back to.");
         }

      } else {
         if (!this.settings.isConfigured()) {
            this.getLogger().warning("YuDream bridge is not fully configured. Set base-url, server-id and api-key in config.yml.");
         }

      }
   }

   public boolean canReport() {
      if (this.settings != null && this.settings.isEnabled() && this.settings.isConfigured()) {
         if (!this.settings.isDownstream()) {
            return true;
         } else {
            return this.settings.getDownstream().isFallbackToApi() && !this.isProxyReachable();
         }
      } else {
         return false;
      }
   }

   private boolean isProxyReachable() {
      return this.sensor != null && this.sensor.isProxyReachable();
   }

   public synchronized boolean setMode(BridgeMode mode) {
      File configFile = new File(this.getDataFolder(), "config.yml");

      try {
         YamlPatcher.setTopLevelValue(configFile, "mode", mode.getId());
      } catch (IOException e) {
         this.getLogger().log(Level.WARNING, "Could not write mode=" + mode.getId() + " to " + configFile, e);
         return false;
      }

      this.reloadBridge();
      if (this.settings.isSyncOnlineOnEnable()) {
         this.syncOnlinePlayers();
      }

      return true;
   }

   public void handleJoin(Player player) {
      if (this.sensor != null) {
         this.sensor.onPlayerJoin(player);
      }

      if (this.localPlaytimeEnabled()) {
         long now = System.currentTimeMillis();
         // AFK 状态机不跨重启保留：重新进服时先把上一段没结束的挂机区间就地结算，
         // 否则这段挂着的时间会一直按"正在挂机"累加下去（后端在快照校正时早已结清）。
         if (this.playtimeLedger.isAfk(player.getUniqueId())) {
            this.playtimeLedger.endAfk(player.getUniqueId(), player.getName(), now);
         }

         this.playtimeLedger.join(player.getUniqueId(), player.getName(), now);
      }

      // AFK 计时不再依赖后端是否可用：后端挂着的时候本地也要能统计在线 / 挂机时长。
      if (this.afkTracker != null) {
         this.afkTracker.markOnline(player);
      }

      if (this.canReport()) {
         this.reportQueue.submit(PlayerEventType.JOIN, PlayerSnapshots.from(player));
      }

   }

   public void handleQuit(Player player) {
      if (this.sensor != null) {
         this.sensor.onPlayerQuit(player);
      }

      if (this.localPlaytimeEnabled()) {
         this.playtimeLedger.quit(player.getUniqueId(), player.getName(), System.currentTimeMillis());
      }

      if (this.afkTracker != null) {
         this.afkTracker.markOffline(player);
      }

      if (this.canReport()) {
         this.reportQueue.submit(PlayerEventType.QUIT, PlayerSnapshots.from(player));
      }

   }

   public void handleActivity(Player player, String source) {
      if (this.sensor != null) {
         this.sensor.onActivity(player, source);
      }

      if (this.afkTracker != null) {
         this.afkTracker.markActive(player);
      }

   }

   /** 本地台账是否生效（与后端配置无关，只看本地开关）。 */
   private boolean localPlaytimeEnabled() {
      return this.playtimeLedger != null && this.settings != null && this.settings.getLocalPlaytime().isEnabled();
   }

   private void recordAfk(boolean start, Player player, long eventAt) {
      if (!this.localPlaytimeEnabled()) {
         return;
      }

      if (start) {
         this.playtimeLedger.startAfk(player.getUniqueId(), player.getName(), eventAt);
      } else {
         this.playtimeLedger.endAfk(player.getUniqueId(), player.getName(), eventAt);
      }
   }

   private void startPlaytimeSaveTask() {
      if (this.playtimeLedger == null) {
         return;
      }

      long interval = this.settings == null ? 600L : this.settings.getLocalPlaytime().getSaveIntervalTicks();
      this.playtimeSaveTask = FoliaSchedulerCompat.runAsyncRepeating(this, new Runnable() {
         public void run() {
            if (YudreamMinecraftPlugin.this.playtimeLedger != null) {
               YudreamMinecraftPlugin.this.playtimeLedger.saveIfDirty();
            }
         }
      }, interval, interval);
   }

   private void stopPlaytimeSaveTask() {
      if (this.playtimeSaveTask != null) {
         FoliaSchedulerCompat.cancel(this.playtimeSaveTask);
         this.playtimeSaveTask = null;
      }
   }

   /**
    * 定期把后端的每玩家累计拉下来对齐本地台账（"服务器同步后端数据"）。
    *
    * <p>在线玩家集合在主线程采集后交给异步任务，避免在异步线程里读服务端状态。
    */
   private void startPlaytimeSyncTask() {
      if (this.playtimeSync == null || this.settings == null || !this.settings.getLocalPlaytime().isSyncFromBackend()) {
         return;
      }

      long interval = this.settings.getLocalPlaytime().getSyncIntervalTicks();
      this.playtimeSyncTask = FoliaSchedulerCompat.runGlobalRepeating(this, new Runnable() {
         public void run() {
            final Set<UUID> online = new HashSet<>();
            for (Player player : YudreamMinecraftPlugin.this.getServer().getOnlinePlayers()) {
               online.add(player.getUniqueId());
            }

            FoliaSchedulerCompat.runAsync(YudreamMinecraftPlugin.this, new Runnable() {
               public void run() {
                  if (YudreamMinecraftPlugin.this.playtimeSync != null) {
                     YudreamMinecraftPlugin.this.playtimeSync.pullFromBackend(online);
                  }
               }
            });
         }
      }, 200L, interval);
   }

   private void stopPlaytimeSyncTask() {
      if (this.playtimeSyncTask != null) {
         FoliaSchedulerCompat.cancel(this.playtimeSyncTask);
         this.playtimeSyncTask = null;
      }
   }

   public void handleGameChat(Player player, String message) {
      this.reportGameEvent(PlayerEventType.CHAT, player, message);
   }

   public void handleGameDeath(Player player, String deathMessage) {
      this.reportGameEvent(PlayerEventType.DEATH, player, deathMessage);
   }

   public void handleGameAdvancement(Player player, String title) {
      this.reportGameEvent(PlayerEventType.ADVANCEMENT, player, title);
   }

   private void reportGameEvent(PlayerEventType type, Player player, String content) {
      if (content != null && !content.trim().isEmpty()) {
         PlayerEventPayload payload = new PlayerEventPayload(player.getUniqueId().toString(), player.getName(), System.currentTimeMillis(), content.trim());
         if (this.sensor != null) {
            this.sensor.onGameEvent(type, payload);
         }

         if (this.canReport()) {
            this.reportQueue.submit(type, payload);
         }

      }
   }

   public void syncOnlinePlayers() {
      if (this.settings != null && this.settings.isDownstream()) {
         if (this.sensor != null) {
            this.sensor.requestHello();
         }

      } else if (this.canReport()) {
         for(Player player : this.getServer().getOnlinePlayers()) {
            this.reportQueue.submit(PlayerEventType.JOIN, PlayerSnapshots.from(player));
            this.afkTracker.markOnline(player);
         }

         this.reportPlayerSnapshot();
      }
   }

   private void reportPlayerSnapshot() {
      if (this.canReport() && this.reportQueue != null) {
         long observedAt = System.currentTimeMillis();
         List<PlayerEventPayload> players = new ArrayList();

         for(Player player : this.getServer().getOnlinePlayers()) {
            players.add(PlayerSnapshots.from(player, observedAt));
         }

         this.reportQueue.submitSnapshot(players, observedAt);
      }
   }

   public YudreamConfig getSettings() {
      return this.settings;
   }

   public YudreamApiClient getApiClient() {
      return this.apiClient;
   }

   public ReportQueue getReportQueue() {
      return this.reportQueue;
   }

   public AfkTracker getAfkTracker() {
      return this.afkTracker;
   }

   /** 本地在线时长台账（与后端同口径；伴侣插件也读它的落盘文件）。 */
   public PlaytimeLedger getPlaytimeLedger() {
      return this.playtimeLedger;
   }

   /** 待发上报的落盘日志；未启用时返回 {@code null}。 */
   public PendingReportJournal getReportJournal() {
      return this.reportJournal;
   }

   /** 本地台账与后端对齐的进度（命令里显示最近一次结果）。 */
   public PlaytimeSync getPlaytimeSync() {
      return this.playtimeSync;
   }

   public DownstreamSensor getSensor() {
      return this.sensor;
   }

   public boolean isDownstreamMode() {
      return this.settings != null && this.settings.isDownstream();
   }
}
