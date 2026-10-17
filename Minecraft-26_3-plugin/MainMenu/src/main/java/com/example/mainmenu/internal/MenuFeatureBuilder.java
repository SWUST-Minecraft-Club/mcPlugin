package com.example.mainmenu.internal;

import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * {@link MenuFeature.Builder} 的实现。
 */
public final class MenuFeatureBuilder implements MenuFeature.Builder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final String id;
    private String owner;
    private ItemStack icon = new ItemStack(Material.STONE);
    private Component name;
    private final List<Component> lines = new ArrayList<>();
    private boolean clickable;
    private BiConsumer<Player, ClickType> handler;
    private Integer preferredSlot;
    private int order;

    public MenuFeatureBuilder(final String id) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("功能 id 不能为空");
        }
    }

    @Override
    public MenuFeature.Builder owner(final String owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public MenuFeature.Builder icon(final ItemStack icon) {
        this.icon = Objects.requireNonNull(icon, "icon").clone();
        return this;
    }

    @Override
    public MenuFeature.Builder icon(final Material material) {
        return icon(new ItemStack(Objects.requireNonNull(material, "material")));
    }

    @Override
    public MenuFeature.Builder name(final String miniMessage) {
        return name(MINI.deserialize(Objects.requireNonNull(miniMessage, "miniMessage")));
    }

    @Override
    public MenuFeature.Builder name(final Component name) {
        this.name = Objects.requireNonNull(name, "name");
        return this;
    }

    @Override
    public MenuFeature.Builder line(final String miniMessage) {
        return line(MINI.deserialize(Objects.requireNonNull(miniMessage, "miniMessage")));
    }

    @Override
    public MenuFeature.Builder line(final Component line) {
        this.lines.add(Objects.requireNonNull(line, "line"));
        return this;
    }

    @Override
    public MenuFeature.Builder lines(final List<Component> lines) {
        this.lines.clear();
        if (lines != null) {
            lines.forEach(this::line);
        }
        return this;
    }

    @Override
    public MenuFeature.Builder clickable(final boolean clickable) {
        this.clickable = clickable;
        return this;
    }

    @Override
    public MenuFeature.Builder onClick(final BiConsumer<Player, ClickType> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.clickable = true;
        return this;
    }

    @Override
    public MenuFeature.Builder preferredSlot(final int slot) {
        this.preferredSlot = slot;
        return this;
    }

    @Override
    public MenuFeature.Builder order(final int order) {
        this.order = order;
        return this;
    }

    @Override
    public MenuFeature build() {
        if (clickable && handler == null) {
            throw new IllegalStateException("功能 " + id + " 标记为可点击，但没有设置 onClick");
        }
        if (!clickable) {
            handler = null;
        }

        // 显示名写进物品本身，悬停文本交给主菜单在渲染时拼接（方便追加冲突提示）
        final ItemStack built = icon.clone();
        final ItemMeta meta = built.getItemMeta();
        if (meta != null) {
            final Component display = noItalic(name != null ? name : Component.text(id));
            meta.displayName(display);
            built.setItemMeta(meta);
        }

        final List<Component> description = List.copyOf(lines);
        return new SimpleMenuFeature(
                id,
                owner != null ? owner : defaultOwner(id),
                built,
                description,
                clickable,
                handler,
                preferredSlot != null ? OptionalInt.of(preferredSlot) : OptionalInt.empty(),
                order
        );
    }

    private static String defaultOwner(final String id) {
        final int split = id.indexOf(':');
        return split > 0 ? id.substring(0, split) : "unknown";
    }

    private static Component noItalic(final Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
