package com.example.town.util;

import com.example.town.TownPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Folia 区域化调度的小封装：全仓禁止 Bukkit.getScheduler()，统一走这里。
 */
public final class Threads {

    private Threads() {
    }

    /** 全局任务：跨区域维护、定时器。 */
    public static void global(TownPlugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    /** 全局延迟任务（tick）。 */
    public static void globalLater(TownPlugin plugin, long delayTicks, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
    }

    /**
     * 玩家区域任务：背包 / 消息 / 传送 / 实体状态。
     * 已在归属线程时直接执行（不多绕一 tick）。
     */
    public static void forPlayer(TownPlugin plugin, Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            player.getScheduler().run(plugin, t -> task.run(), null);
        }
    }

    /** 纯 IO / 网络。 */
    public static void async(TownPlugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }
}
