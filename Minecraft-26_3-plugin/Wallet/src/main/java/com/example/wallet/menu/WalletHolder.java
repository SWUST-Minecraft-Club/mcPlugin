package com.example.wallet.menu;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Wallet 自己打开的界面的持有者（流水页 / 货币类型页 / 玩家货币页）。 */
public final class WalletHolder implements InventoryHolder {

    public enum Kind {
        HISTORY,
        ASSETS,
        PLAYERS
    }

    private final Kind kind;
    private final int page;
    private final UUID viewer;
    private Inventory inventory;
    private String currency;

    public WalletHolder(final Kind kind, final int page, final UUID viewer) {
        this.kind = kind;
        this.page = page;
        this.viewer = viewer;
    }

    public Kind kind() {
        return kind;
    }

    public int page() {
        return page;
    }

    public UUID viewer() {
        return viewer;
    }

    public String currency() { return currency; }
    public void currency(String value) { currency = value; }

    public void inventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
