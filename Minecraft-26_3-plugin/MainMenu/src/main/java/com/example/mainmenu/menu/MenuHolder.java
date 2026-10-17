package com.example.mainmenu.menu;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/**
 * 主菜单的 {@link InventoryHolder}：既是“这是主菜单”的标记，也保存查看者与当前页。
 */
public final class MenuHolder implements InventoryHolder {

    private final UUID viewerId;
    private final String viewerName;
    private @Nullable Inventory inventory;
    private int page = 1;

    public MenuHolder(final UUID viewerId, final String viewerName) {
        this.viewerId = viewerId;
        this.viewerName = viewerName;
    }

    @Override
    public Inventory getInventory() {
        Inventory current = inventory;
        if (current == null) {
            // 正常流程会在创建后立刻 attach；这里兜底，避免第三方在 attach 之前调用时 NPE
            current = Bukkit.createInventory(this, MenuLayout.SIZE);
            inventory = current;
        }
        return current;
    }

    public void attach(final Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String viewerName() {
        return viewerName;
    }

    /** 当前页（从 1 开始） */
    public int page() {
        return page;
    }

    public void page(final int page) {
        this.page = Math.max(1, page);
    }

    public boolean isViewer(final Player player) {
        return player.getUniqueId().equals(viewerId);
    }
}
