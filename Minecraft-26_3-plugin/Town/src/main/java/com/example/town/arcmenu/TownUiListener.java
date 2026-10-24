package com.example.town.arcmenu;

import com.example.town.TownPlugin;
import com.example.town.command.TownCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** 玩家离线后不再保留菜单页、地图中心或待确认操作。 */
public final class TownUiListener implements Listener {

    private final TownPlugin plugin;

    public TownUiListener(final TownPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        TownCommand.clearConfirm(event.getPlayer());
        if (plugin.ui() != null) {
            plugin.ui().forget(event.getPlayer().getUniqueId());
        }
    }
}
