package online.yudream.minecraft.bukkit.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 本地在线时长统计的开关与落盘节奏。
 *
 * <p>本地统计与后端是否可用无关：后端挂了照常记账，等后端恢复再把事件补发上去，
 * 后端按同一套结算规则算出的数字会与本地一致。
 */
public final class LocalPlaytimeOptions {

   private final boolean enabled;
   private final long saveIntervalTicks;
   private final boolean syncFromBackend;
   private final long syncIntervalTicks;

   private LocalPlaytimeOptions(boolean enabled, long saveIntervalTicks, boolean syncFromBackend, long syncIntervalTicks) {
      this.enabled = enabled;
      this.saveIntervalTicks = saveIntervalTicks;
      this.syncFromBackend = syncFromBackend;
      this.syncIntervalTicks = syncIntervalTicks;
   }

   public static LocalPlaytimeOptions defaults() {
      return new LocalPlaytimeOptions(true, 600L, true, 6000L);
   }

   public static LocalPlaytimeOptions load(FileConfiguration config) {
      boolean enabled = config.getBoolean("local-playtime.enabled", true);
      long seconds = config.getLong("local-playtime.save-interval-seconds", 30L);
      if (seconds <= 0L) {
         seconds = 30L;
      }

      boolean sync = config.getBoolean("local-playtime.sync-from-backend", true);
      long syncSeconds = config.getLong("local-playtime.sync-interval-seconds", 300L);
      if (syncSeconds <= 0L) {
         syncSeconds = 300L;
      }

      return new LocalPlaytimeOptions(enabled, seconds * 20L, sync, syncSeconds * 20L);
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   /** 落盘间隔，单位 tick（1 秒 = 20 tick）。 */
   public long getSaveIntervalTicks() {
      return this.saveIntervalTicks;
   }

   /** 是否定期把后端的每玩家累计拉下来对齐本地台账（服务器同步后端数据）。 */
   public boolean isSyncFromBackend() {
      return this.syncFromBackend;
   }

   /** 对齐间隔，单位 tick。 */
   public long getSyncIntervalTicks() {
      return this.syncIntervalTicks;
   }
}
