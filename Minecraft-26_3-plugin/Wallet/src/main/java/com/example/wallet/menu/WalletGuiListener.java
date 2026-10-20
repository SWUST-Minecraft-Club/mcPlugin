package com.example.wallet.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Wallet 三个界面的点击处理：一律取消原版交互，只响应 47 上一页 / 49 返回 / 51 下一页。 */
public final class WalletGuiListener implements Listener {

    private final WalletMenus menus;

    public WalletGuiListener(final WalletMenus menus) {
        this.menus = menus;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WalletHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 47 -> this.menus.reopen(player, holder.kind(), holder.page() - 1);
            case 49 -> this.menus.back(player);
            case 51 -> this.menus.reopen(player, holder.kind(), holder.page() + 1);
            default -> {
                // 其他格子只展示，不响应
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WalletHolder) {
            event.setCancelled(true);
        }
    }
}
