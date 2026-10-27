package com.example.wallet.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Wallet 菜单取消所有取放与拖拽；普通点击响应分页、返回、筛选和币种开关。 */
public final class WalletGuiListener implements Listener {

    private final WalletMenus menus;

    public WalletGuiListener(final WalletMenus menus) {
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (!holder.viewer().equals(player.getUniqueId())) return;

        // Open/close views after the event has finished, on this player's Folia thread.
        final int slot = event.getRawSlot();
        this.menus.clickLater(player, holder, () -> {
            switch (slot) {
                case 45 -> {
                    if (holder.kind() == WalletHolder.Kind.HISTORY) this.menus.cycleHistoryCurrency(player, holder);
                }
                case 47 -> this.menus.reopen(player, holder, holder.page() - 1);
                case 49 -> this.menus.back(player);
                case 51 -> this.menus.reopen(player, holder, holder.page() + 1);
                default -> {
                    if (holder.kind() == WalletHolder.Kind.ASSETS) {
                        this.menus.toggleAsset(player, holder, slot);
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WalletHolder) {
            event.setCancelled(true);
        }
    }
}
