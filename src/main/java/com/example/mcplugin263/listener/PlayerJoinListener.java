package com.example.mcplugin263.listener;

import com.example.mcplugin263.MCPlugin263;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家进服事件示例。
 */
public final class PlayerJoinListener implements Listener {

    private final MCPlugin263 plugin;

    public PlayerJoinListener(MCPlugin263 plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("welcome.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        player.sendMessage(plugin.render("welcome.message", Placeholder.unparsed("player", player.getName())));
    }
}
