package com.example.mainmenu.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia 线程调度统一入口。
 *
 * <p>主菜单的界面操作（创建 / 填充 / 打开 / 重绘背包）必须在该玩家所属的区域线程执行，
 * 所以玩家相关任务一律走 {@link Player#getScheduler()}（实体调度器）；已经在该玩家的
 * 区域线程上时直接执行，避免多等一 tick。
 *
 * <p>不要再用 {@code Bukkit.getScheduler()}：Folia 上它不可用。
 */
public final class Schedulers {

    private Schedulers() {
    }

    /** 当前线程是否拥有该玩家所属区域。 */
    public static boolean ownedBy(final Player player) {
        return Bukkit.isOwnedByCurrentRegion(player);
    }

    /** 在该玩家的区域线程执行（已在该线程时立即执行）。 */
    public static void runFor(final Plugin plugin, final Player player, final Runnable task) {
        if (ownedBy(player)) {
            task.run();
            return;
        }
        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /** 延迟若干 tick 在该玩家的区域线程执行。 */
    public static ScheduledTask runForDelayed(final Plugin plugin, final Player player,
                                              final Runnable task, final long delayTicks) {
        return player.getScheduler().runDelayed(plugin,
                scheduled -> task.run(), null, Math.max(1L, delayTicks));
    }

    /** 以固定周期在该玩家的区域线程执行。 */
    public static ScheduledTask runForAtFixedRate(final Plugin plugin, final Player player,
                                                  final Runnable task, final long initialDelayTicks,
                                                  final long periodTicks) {
        return player.getScheduler().runAtFixedRate(plugin,
                scheduled -> task.run(), null,
                Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    /** 取消任务（null 安全）。 */
    public static void cancel(final ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
