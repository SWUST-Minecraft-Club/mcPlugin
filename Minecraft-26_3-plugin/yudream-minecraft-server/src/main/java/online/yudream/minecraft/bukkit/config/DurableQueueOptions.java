package online.yudream.minecraft.bukkit.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 待发事件落盘（durable queue）的开关与容量。
 *
 * <p>后端不可用、未配置或网络失败时，进服 / 退服 / AFK 这类会影响时长的上报先写进本地
 * 待发日志，后端恢复后按原始时间戳按序补发；进程重启也不丢。
 *
 * <p>聊天 / 死亡 / 成就这类「消息型」事件不落盘：补发它们等于把过期消息重新推给群聊，
 * 没有意义，保持原来的尽力而为语义。
 */
public final class DurableQueueOptions {

   private final boolean enabled;
   private final int maxPending;

   private DurableQueueOptions(boolean enabled, int maxPending) {
      this.enabled = enabled;
      this.maxPending = maxPending;
   }

   public static DurableQueueOptions defaults() {
      return new DurableQueueOptions(true, 5000);
   }

   public static DurableQueueOptions load(FileConfiguration config) {
      boolean enabled = config.getBoolean("durable-queue.enabled", true);
      int maxPending = config.getInt("durable-queue.max-pending", 5000);
      if (maxPending <= 0) {
         maxPending = 5000;
      }

      return new DurableQueueOptions(enabled, maxPending);
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public int getMaxPending() {
      return this.maxPending;
   }
}
