package com.example.town.hook;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.town.TownPlugin;
import com.example.town.model.TownSnapshotView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * MainMenu 软依赖隔离层：把「小镇」入口挂到主菜单功能区。
 * com.example.mainmenu 类型只能出现在本类（见 exp/paper-plugin.md 软依赖类加载）。
 */
public final class MainMenuHook {

    private static final String FEATURE_ID = "town:main";

    private final TownPlugin plugin;
    private volatile MenuFeature feature;

    private MainMenuHook(TownPlugin plugin) {
        this.plugin = plugin;
    }

    public static MainMenuHook create(TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("MainMenu") == null) {
            return null;
        }
        try {
            plugin.getLogger().info("已接入 MainMenu：主菜单将出现小镇入口。");
            return new MainMenuHook(plugin);
        } catch (LinkageError e) {
            plugin.getLogger().warning("MainMenu API 类不可用，主菜单入口禁用："
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    public void register() {
        feature = MenuFeature.builder(FEATURE_ID)
                .owner(plugin.getPluginMeta().getName())
                .icon(Material.BELL)
                .name("<gold>小镇")
                .linesForViewer(this::linesFor)
                .order(plugin.config().menuEntryOrder())
                .onClick((player, clickType) -> plugin.openUi(player))
                .build();
        MainMenuApi.registry().register(feature);
    }

    public void unregister() {
        var registry = MainMenuApi.registryOrNull();
        if (registry != null) {
            registry.unregister(FEATURE_ID);
        }
        feature = null;
    }

    public boolean open(Player player) {
        try { return MainMenuApi.openOrNull(player); }
        catch(LinkageError error) { return false; }
    }

    private List<net.kyori.adventure.text.Component> linesFor(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        TownSnapshotView town = plugin.service().townOf(viewerId);
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        var mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        if (town == null) {
            TownSnapshotView invited = plugin.service().inviteOf(viewerId);
            if (invited != null) {
                lines.add(mini.deserialize("<gray>小镇 <white>" + invited.name()
                        + "</white> 邀请你加入"));
                lines.add(mini.deserialize("<yellow>点击打开界面接受邀请"));
            } else {
                lines.add(mini.deserialize("<gray>创立或加入一个小镇"));
                lines.add(mini.deserialize("<yellow>点击创建你的小镇"));
            }
            return lines;
        }
        lines.add(mini.deserialize("<gold>" + town.name()
                + (town.active() ? "" : " <gray>(筹备中)")));
        String mayor = Bukkit.getOfflinePlayer(town.mayor()).getName();
        lines.add(mini.deserialize("<gray>镇长：<white>" + (mayor == null ? "?" : mayor)));
        lines.add(mini.deserialize("<gray>成员：<white>" + town.memberCount()
                + "</white> · 领地：<white>" + town.claimCount() + "</white> 块"));
        if (!town.buffs().isEmpty()) {
            lines.add(mini.deserialize("<gray>祝福：<white>" + town.buffs().size() + " 个生效中"));
        }
        lines.add(mini.deserialize("<yellow>点击打开小镇界面"));
        return lines;
    }
}
