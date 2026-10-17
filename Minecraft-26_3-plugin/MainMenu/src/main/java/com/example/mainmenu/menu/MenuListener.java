package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.MenuFeature;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 菜单交互：翻页、功能点击、禁止移动菜单里的物品。
 */
public final class MenuListener implements Listener {

    private final MainMenuPlugin plugin;

    public MenuListener(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        // 菜单里的物品一律不可移动（含 shift 点击、数字键交换等）
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !holder.isViewer(player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // 点的是自己背包
        }

        final int slot = event.getSlot();
        if (slot == MenuLayout.PREVIOUS_PAGE_SLOT) {
            if (holder.page() > 1) {
                plugin.menus().show(player, holder, holder.page() - 1);
            }
            return;
        }
        if (slot == MenuLayout.NEXT_PAGE_SLOT) {
            if (holder.page() < plugin.features().pageCount()) {
                plugin.menus().show(player, holder, holder.page() + 1);
            }
            return;
        }

        final Optional<MenuFeature> feature = plugin.features().featureAt(slot, holder.page());
        if (feature.isEmpty()) {
            return;
        }
        final MenuFeature target = feature.get();
        if (!target.clickable()) {
            return; // 固定/展示型功能：点击无效
        }
        try {
            target.onClick(player, event.getClick());
        } catch (final RuntimeException exception) {
            plugin.getLogger().warning("功能 " + target.id() + " 点击处理出错：" + exception);
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            plugin.menus().stopRefresh(event.getPlayer().getUniqueId());
        }
    }
}
