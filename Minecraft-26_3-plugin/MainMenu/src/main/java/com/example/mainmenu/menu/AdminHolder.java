package com.example.mainmenu.menu;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/**
 * 管理界面的 {@link InventoryHolder}：记录当前是哪一页、在管理哪个功能。
 */
public final class AdminHolder implements InventoryHolder {

    /** 界面类型 */
    public enum Mode {
        /** 管理列表（左右两栏） */
        MANAGE,
        /** 非固定位置功能插件的属性页 */
        ATTRIBUTE_FUNCTION,
        /** 固定位置功能插件的属性页 */
        ATTRIBUTE_FIXED,
        /** 占位符属性页：只有排序值与删除 */
        ATTRIBUTE_PLACEHOLDER,
        /** 位置选择界面 */
        POSITION_PICKER
    }

    private final UUID viewerId;
    private final Mode mode;
    private final @Nullable String featureId;
    private @Nullable Inventory inventory;
    private int leftPage = 1;
    private int rightPage = 1;

    public AdminHolder(final UUID viewerId, final Mode mode, final @Nullable String featureId) {
        this.viewerId = viewerId;
        this.mode = mode;
        this.featureId = featureId;
    }

    @Override
    public Inventory getInventory() {
        Inventory current = inventory;
        if (current == null) {
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

    public Mode mode() {
        return mode;
    }

    /** 正在管理的功能 id（管理列表页为 null） */
    public @Nullable String featureId() {
        return featureId;
    }

    public int leftPage() {
        return leftPage;
    }

    public void leftPage(final int page) {
        this.leftPage = Math.max(1, page);
    }

    public int rightPage() {
        return rightPage;
    }

    public void rightPage(final int page) {
        this.rightPage = Math.max(1, page);
    }

    public boolean isViewer(final Player player) {
        return player.getUniqueId().equals(viewerId);
    }
}
