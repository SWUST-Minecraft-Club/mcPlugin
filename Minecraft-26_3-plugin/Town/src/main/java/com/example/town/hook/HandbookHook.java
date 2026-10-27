package com.example.town.hook;

import com.example.town.TownPlugin;
import org.bukkit.entity.Player;

/** 可选手册入口；外部 API 的类引用只存在于确认依赖启用后才加载的 Adapter 中。 */
public final class HandbookHook {
    private final TownPlugin plugin;

    private HandbookHook(TownPlugin plugin) { this.plugin = plugin; }

    public static HandbookHook create(TownPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HandbookCore")) return null;
        HandbookHook hook = new HandbookHook(plugin);
        if (!hook.register()) return null;
        plugin.getLogger().info("已向 HandbookCore 注册小镇玩家指南（14 篇）。");
        return hook;
    }

    public boolean register() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HandbookCore")) return false;
        try {
            Adapter.register(plugin);
            return true;
        } catch (LinkageError | RuntimeException error) {
            plugin.getLogger().warning("小镇玩家指南注册失败：" + error.getClass().getSimpleName());
            return false;
        }
    }

    public void unregister() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("HandbookCore")) Adapter.unregister(plugin);
    }

    public boolean open(Player player) {
        return plugin.getServer().getPluginManager().isPluginEnabled("HandbookCore") && Adapter.open(player);
    }

    private static final class Adapter {
        private static void register(TownPlugin plugin) {
            com.example.handbookcore.api.HandbookApi.service().replace(plugin,
                    com.example.town.handbook.TownGuide.create(plugin.config()));
        }

        private static void unregister(TownPlugin plugin) {
            var service = com.example.handbookcore.api.HandbookApi.serviceOrNull();
            if (service != null) service.unregisterAll(plugin);
        }

        private static boolean open(Player player) {
            var service = com.example.handbookcore.api.HandbookApi.serviceOrNull();
            if (service == null) return false;
            service.openEntry(player, com.example.town.handbook.TownGuide.START);
            return true;
        }
    }
}
