package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.playtime.PlaytimeInfo;
import com.example.mainmenu.playtime.PlaytimeService;
import com.example.mainmenu.util.Durations;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
        items[MenuLayout.CLOCK_SLOT] = clockItem();
        items[MenuLayout.PAINTING_SLOT] = paintingItem(viewer);
        items[MenuLayout.PREVIOUS_PAGE_SLOT] = pageArrow(true, page);
        items[MenuLayout.NEXT_PAGE_SLOT] = pageArrow(false, page);

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

    /** 第 1 行第 2 格：当前时间（东 8 区） */
    public ItemStack clockItem() {
        final ItemStack item = plugin.configItem("menu.clock");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final List<Component> lore = new ArrayList<>(2);
            lore.add(plugin.miniRaw(plugin.string("menu.clock.line", "<gray>当前时间：<white>{time}</white></gray>")
                    .replace("{time}", currentTime())));
            addHint(lore, "menu.clock.hint");
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 第 1 行第 5 格：在线时长（来自 YuDream 后端） */
    public ItemStack paintingItem(final Player viewer) {
        final ItemStack item = plugin.configItem("menu.painting");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final List<Component> lore = new ArrayList<>(4);
            final PlaytimeService.Result result = plugin.playtime().get(viewer);
            switch (result.status()) {
                case NOT_CONFIGURED -> lore.add(plugin.mini("messages.playtime-not-configured"));
                case LOADING -> lore.add(plugin.mini("messages.playtime-loading"));
                case UNAVAILABLE -> lore.add(plugin.mini("messages.playtime-unavailable"));
                case OK -> appendPlaytime(lore, result.info());
            }
            addHint(lore, "menu.painting.hint");
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void appendPlaytime(final List<Component> lore, final PlaytimeInfo info) {
        final long now = System.currentTimeMillis();
        lore.add(plugin.miniRaw(plugin.string("menu.painting.lines.total",
                        "<gray>累计在线：<white>{duration}</white></gray>")
                .replace("{duration}", Durations.format(info.totalOnlineMillis()))));
        if (info.online()) {
            lore.add(plugin.miniRaw(plugin.string("menu.painting.lines.session",
                            "<gray>本次会话：<white>{duration}</white></gray>")
                    .replace("{duration}", Durations.format(info.currentSessionMillis(now)))));
        }
        lore.add(plugin.miniRaw(plugin.string("menu.painting.lines.afk",
                        "<gray>累计挂机：<white>{duration}</white></gray>")
                .replace("{duration}", Durations.format(info.totalAfkMillis()))));
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

    private String currentTime() {
        final int offsetHours = plugin.getConfig().getInt("menu.clock.zone-offset-hours", 8);
        final String pattern = plugin.string("menu.clock.format", "yyyy-MM-dd HH:mm:ss");
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneOffset.ofHours(offsetHours))
                .format(Instant.now());
    }

    private static Material material(final String name, final Material fallback) {
        final Material material = Material.matchMaterial(name);
        return material == null || material.isAir() ? fallback : material;
    }

    private static Component noItalic(final Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
