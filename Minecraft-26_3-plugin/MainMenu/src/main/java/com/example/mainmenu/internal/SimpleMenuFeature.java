package com.example.mainmenu.internal;

import com.example.mainmenu.api.MenuFeature;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
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
    public void onClick(final Player player, final ClickType clickType) {
        if (handler != null) {
            handler.accept(player, clickType);
        }
    }
}
