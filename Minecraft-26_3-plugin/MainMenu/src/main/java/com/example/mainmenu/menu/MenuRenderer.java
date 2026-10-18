package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 把当前页渲染成 54 个格子。
 *
 * <p>布局见 {@link MenuLayout}：固定功能位（时钟 / 在线时长 / 翻页箭头）+ 非固定空位（白色玻璃板）
 * + 功能区（功能插件注册的功能，按页显示）。
 */
public final class MenuRenderer {

    private final MainMenuPlugin plugin;

    public MenuRenderer(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    /** 渲染整页 */
    public ItemStack[] render(final Player viewer, final int page) {
        final ItemStack[] items = new ItemStack[MenuLayout.SIZE];

        // 非固定功能位：白色染色玻璃板（可被自定义位置的功能占用，所以先铺、后覆盖）
        final ItemStack filler = plugin.configItem("menu.filler");
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (MenuLayout.isFreeSlot(slot)) {
                items[slot] = filler.clone();
            }
        }

        // 固定功能位
        items[MenuLayout.PREVIOUS_PAGE_SLOT] = pageArrow(true, page);
        items[MenuLayout.NEXT_PAGE_SLOT] = pageArrow(false, page);
        // 第 6 行第 5 格：功能插件管理入口，只有管理员看得到（其他人看到的是普通玻璃板）
        items[MenuLayout.ADMIN_SLOT] = viewer.hasPermission("mainmenu.admin")
                ? adminItem()
                : plugin.configItem("menu.filler");

        // 自定义位置的功能（每页都显示）
        for (final MenuFeature feature : plugin.features().customPositionFeatures()) {
            place(items, feature);
        }
        // 功能区本页的功能
        for (final MenuFeature feature : plugin.features().featuresOnPage(page)) {
            place(items, feature);
        }
        return items;
    }

    // ------------------------------------------------------------------ 固定功能位

    /** 第 6 行第 5 格：功能插件管理入口（仅管理员可见） */
    private ItemStack adminItem() {
        final ItemStack item = plugin.configItem("menu.admin.entry");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final List<Component> lore = new ArrayList<>(2);
            lore.add(plugin.miniRaw("<gray>点击进入功能插件管理</gray>"));
            lore.add(plugin.miniRaw("<dark_gray>仅管理员可见</dark_gray>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 第 4 行第 1 / 第 9 格：翻页箭头 */
    public ItemStack pageArrow(final boolean previous, final int page) {
        final int pages = plugin.features().pageCount();
        final boolean enabled = previous ? page > 1 : page < pages;

        final String materialKey = previous ? "menu.paging.previous-material" : "menu.paging.next-material";
        final ItemStack item = new ItemStack(material(plugin.string(materialKey, "ARROW"), Material.ARROW));
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.mini(previous ? "menu.paging.previous-name" : "menu.paging.next-name"));
            final List<Component> lore = new ArrayList<>(2);
            lore.add(plugin.miniRaw(plugin.string("menu.paging.page-line",
                            "<gray>第 <white>{page}</white>/<white>{pages}</white> 页</gray>")
                    .replace("{page}", String.valueOf(page))
                    .replace("{pages}", String.valueOf(pages))));
            if (!enabled) {
                lore.add(plugin.mini(previous ? "menu.paging.first-page" : "menu.paging.last-page"));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------ 功能物品

    private void place(final ItemStack[] items, final MenuFeature feature) {
        final Optional<FeaturePlacement> placement = plugin.features().placement(feature.id());
        if (placement.isEmpty()) {
            return;
        }
        final int slot = placement.get().slot();
        if (slot < 0 || slot >= MenuLayout.SIZE) {
            return;
        }
        items[slot] = featureItem(feature, placement.get().movedToFunctionArea());
    }

    /**
     * 功能物品：保留功能插件自己的物品外观，悬停文本 = 功能自带文本 + 点击提示 +（位置冲突提示）。
     */
    private ItemStack featureItem(final MenuFeature feature, final boolean movedToFunctionArea) {
        final ItemStack item = feature.icon();
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        final List<Component> lore = new ArrayList<>();
        final List<Component> own = feature.description();
        if (own != null && !own.isEmpty()) {
            own.forEach(line -> lore.add(noItalic(line)));
        } else if (meta.lore() != null) {
            // 功能插件直接把文本写在物品里时，保留它
            meta.lore().forEach(line -> lore.add(noItalic(line)));
        }
        addHint(lore, feature.clickable() ? "menu.feature.clickable-hint" : "menu.feature.not-clickable-hint");
        if (movedToFunctionArea) {
            // 规范要求：位置不可用时，在悬停文本最后追加提示
            addHint(lore, "menu.feature.conflict-hint");
        }
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ 工具

    private void addHint(final List<Component> lore, final String key) {
        final String raw = plugin.string(key, "");
        if (!raw.isBlank()) {
            lore.add(plugin.miniRaw(raw));
        }
    }

    private static Material material(final String name, final Material fallback) {
        final Material material = Material.matchMaterial(name);
        return material == null || material.isAir() ? fallback : material;
    }

    private static Component noItalic(final Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
