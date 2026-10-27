package com.example.handbookcore.api;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** 物品预览数据；复制 ItemStack，避免注册者或调用者修改共享栈。 */
public final class HandbookItem {
    private final ItemStack fallback;
    private final String craftEngineId;
    private HandbookItem(ItemStack fallback, String craftEngineId) {
        this.fallback = Objects.requireNonNull(fallback, "fallback").clone();
        if (fallback.isEmpty()) throw new IllegalArgumentException("预览物品不能是空气");
        this.craftEngineId = Objects.requireNonNull(craftEngineId, "craftEngineId");
    }
    public static HandbookItem stack(ItemStack stack) { return new HandbookItem(stack, ""); }
    public static HandbookItem vanilla(Material material) { return stack(new ItemStack(material)); }
    public static HandbookItem craftEngine(String id, Material fallback) {
        if (id.isBlank()) throw new IllegalArgumentException("CE 物品 id 不能为空");
        return new HandbookItem(new ItemStack(fallback), id);
    }
    public ItemStack fallback() { return fallback.clone(); }
    public String craftEngineId() { return craftEngineId; }
}
