package com.example.mainmenu.internal;

import com.example.mainmenu.api.MenuFeature;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * {@link MenuFeature} 的不可变实现。
 */
record SimpleMenuFeature(
        String id,
        String owner,
        ItemStack icon,
        List<Component> description,
        Function<Player, ItemStack> iconProvider,
        Function<Player, List<Component>> linesProvider,
        boolean clickable,
        BiConsumer<Player, ClickType> handler,
        OptionalInt preferredSlot,
        int order
) implements MenuFeature {

    @Override
    public ItemStack icon() {
        return icon.clone();
    }

    @Override
    public ItemStack icon(final Player viewer) {
        if (iconProvider == null || viewer == null) {
            return icon();
        }

        final ItemStack provided = iconProvider.apply(viewer);
        return provided == null ? icon() : provided.clone();
    }

    @Override
    public List<Component> description(final Player viewer) {
        if (linesProvider == null || viewer == null) {
            return description;
        }

        final List<Component> provided = linesProvider.apply(viewer);
        return provided == null ? description : provided;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (handler != null) {
            handler.accept(player, clickType);
        }
    }
}
