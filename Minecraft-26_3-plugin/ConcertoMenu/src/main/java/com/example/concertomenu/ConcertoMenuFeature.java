package com.example.concertomenu;

import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 主菜单里的 Concerto 入口：唱片机图标，点击让客户端打开 Concerto 的主界面。
 *
 * <p>Concerto 的界面是客户端模组渲染的，服务端不能直接打开它 —— 这里通过插件消息
 * {@code concerto:menu} 发一条 {@code open} 指令，由客户端模组负责开界面。
 */
public final class ConcertoMenuFeature implements MenuFeature {

    private final ConcertoMenuPlugin plugin;
    private final int order;

    public ConcertoMenuFeature(final ConcertoMenuPlugin plugin, final int order) {
        this.plugin = plugin;
        this.order = order;
    }

    @Override
    public String id() {
        return "concertomenu:menu";
    }

    @Override
    public String owner() {
        return "ConcertoMenu";
    }

    @Override
    public ItemStack icon() {
        final ItemStack item = new ItemStack(Material.JUKEBOX);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("音乐播放器", NamedTextColor.WHITE)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<Component> description() {
        final List<Component> lines = new ArrayList<>(2);
        lines.add(line("点击打开 Concerto 音乐界面"));
        lines.add(Component.text("需要客户端安装 Concerto 模组（2.2.0+）", NamedTextColor.DARK_GRAY)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        return lines;
    }

    @Override
    public boolean clickable() {
        return true;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (clickType.isLeftClick()) {
            this.plugin.openMenu(player);
        }
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return this.order;
    }

    private static Component line(final String text) {
        return Component.text(text, NamedTextColor.GRAY)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
