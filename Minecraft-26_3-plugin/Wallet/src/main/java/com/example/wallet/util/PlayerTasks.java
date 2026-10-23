package com.example.wallet.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia 线程调度小工具：Wallet 的界面操作必须在该玩家的区域线程执行。
 */
public final class PlayerTasks {

    private PlayerTasks() {
    }

    public static boolean ownedBy(final Player player) {
        return Bukkit.isOwnedByCurrentRegion(player);
    }

    /** 在玩家的区域线程执行；已经在该线程时立即执行。 */
    public static void run(final Plugin plugin, final Player player, final Runnable task) {
        if (ownedBy(player)) {
            task.run();
            return;
        }
        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }
}
