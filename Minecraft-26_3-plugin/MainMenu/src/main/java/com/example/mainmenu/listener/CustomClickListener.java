package com.example.mainmenu.listener;

import com.example.mainmenu.MainMenuPlugin;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 诊断用：记录客户端回传的自定义点击动作。
 *
 * <p>ESC 菜单里的 dialog 按钮用的是 {@code DialogAction.customClick(...)}，
 * 客户端点击后会把动作 ID 回传服务端。这行日志可以确认"点击到底有没有到达服务端"。
 */
public final class CustomClickListener implements Listener {

    private final MainMenuPlugin plugin;

    public CustomClickListener(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCustomClick(final PlayerCustomClickEvent event) {
        plugin.getLogger().info("收到自定义点击动作：" + event.getIdentifier()
                + "，来自 " + event.getCommonConnection());
    }
}
