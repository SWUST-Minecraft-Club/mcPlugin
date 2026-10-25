package com.example.town.hook;

import com.example.town.TownPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * CraftEngine 软依赖隔离层：全程反射，不引入 CE 编译期依赖。
 * 物品注册在 CE reload 之前是空的，因此只在运行期（发镇政厅道具时）按需解析。
 */
public final class CraftEngineHook {

    private final java.lang.reflect.Method byId;
    private final java.lang.reflect.Method buildItem;

    private CraftEngineHook(java.lang.reflect.Method byId, java.lang.reflect.Method buildItem) {
        this.byId = byId;
        this.buildItem = buildItem;
    }

    /** CE 未安装 / API 不兼容时返回 null。 */
    public static CraftEngineHook create(TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("CraftEngine") == null) {
            return null;
        }
        try {
            Class<?> itemsClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            java.lang.reflect.Method byId = itemsClass.getMethod("byId", String.class);
            Class<?> defClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");
            java.lang.reflect.Method buildItem = defClass.getMethod("buildBukkitItem");
            plugin.getLogger().info("已接入 CraftEngine：镇政厅优先使用自定义物品。");
            return new CraftEngineHook(byId, buildItem);
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("CraftEngine API 不可用，镇政厅回落原版材质："
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 按 id（namespace:path 或 path）构建 CE 物品；物品不存在或失败返回 null。 */
    public ItemStack buildItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            Object definition = byId.invoke(null, id);
            if (definition == null) {
                return null;
            }
            return buildItem.invoke(definition) instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public boolean hasItem(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        try {
            return byId.invoke(null, id) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
