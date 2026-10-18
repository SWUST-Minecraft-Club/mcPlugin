package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 管理界面的交互：全部转发给 {@link AdminService}，菜单里的物品一律不可移动。
 */
public final class AdminListener implements Listener {

    private final MainMenuPlugin plugin;

    public AdminListener(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !holder.isViewer(player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        plugin.admin().handleClick(player, holder, event.getSlot(), event.getClick());
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminHolder) {
            event.setCancelled(true);
        }
    }
}
