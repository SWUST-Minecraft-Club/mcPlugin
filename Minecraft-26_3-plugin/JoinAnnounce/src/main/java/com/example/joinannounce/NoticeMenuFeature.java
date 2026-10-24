package com.example.joinannounce;

import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 主菜单里的「服务器公告」入口：点击把公告再发给查看者一遍。
 *
 * <p>菜单物品与悬停文本每次渲染都从当前配置现算，所以 {@code /joinannounce reload}
 * 改完文案立刻生效；{@link #order()} 同理，重载时插件会重新注册以刷新排序。
 */
public final class NoticeMenuFeature implements MenuFeature {

    private final JoinAnnouncePlugin plugin;

    public NoticeMenuFeature(final JoinAnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return JoinAnnouncePlugin.MENU_FEATURE_ID;
    }

    @Override
    public String owner() {
        return this.plugin.getPluginMeta().getName();
    }

    @Override
    public ItemStack icon() {
        final AnnouncementSettings.MenuSettings menu = this.plugin.settings().menu();
        final ItemStack item = new ItemStack(menu.material());
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(this.plugin.announcements().render(menu.name(), null)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<Component> description() {
        return description(null);
    }

    /** 每次渲染现算，所以「当前在线」会跟着人数走 */
    @Override
    public List<Component> description(final Player viewer) {
        final List<Component> lines = new ArrayList<>();
        for (final String raw : this.plugin.settings().menu().lines()) {
            if (raw != null && !raw.isBlank()) {
                lines.add(this.plugin.announcements().render(raw, viewer)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        return lines;
    }

    @Override
    public boolean clickable() {
        return true;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (clickType.isLeftClick() || clickType.isRightClick()) {
            this.plugin.announcements().showFromMenu(player);
        }
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return this.plugin.settings().menu().order();
    }
}
