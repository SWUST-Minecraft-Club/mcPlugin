package com.example.mainmenu.arcmenu;

import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Resolves viewer data through the existing registration API. */
record FeatureDisplay(String id, String name, String plainName, List<String> lines, List<String> plainLines,
                      String material, String context, boolean clickable) {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    static FeatureDisplay read(final MenuFeature feature, final Player player) {
        final ItemStack item = feature.icon(player).clone();
        final ItemMeta meta = item.getItemMeta();
        final Component name = meta != null && meta.hasDisplayName() ? meta.displayName()
                : Component.text(feature.id());
        List<Component> description = feature.description(player);
        if (description == null || description.isEmpty()) {
            description = meta != null && meta.hasLore() ? meta.lore() : List.of();
        }
        final List<String> lines = new ArrayList<>();
        final List<String> plainLines = new ArrayList<>();
        for (final Component line : description) {
            lines.add(line == null ? "" : LEGACY.serialize(line));
            plainLines.add(line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line));
        }
        return new FeatureDisplay(feature.id(), LEGACY.serialize(name),
                PlainTextComponentSerializer.plainText().serialize(name), List.copyOf(lines), List.copyOf(plainLines),
                material(item, meta), "GUI", feature.clickable());
    }

    @SuppressWarnings("deprecation")
    private static String material(final ItemStack item, final ItemMeta meta) {
        if (item.getType().isAir()) {
            return "minecraft:paper";
        }
        if (meta instanceof SkullMeta skull) {
            final var profile = skull.getPlayerProfile();
            if (profile != null) {
                for (final var property : profile.getProperties()) {
                    if (property.getName().equals("textures")) {
                        return "<skull:" + property.getValue() + ">";
                    }
                }
            }
            if (skull.getOwningPlayer() != null) {
                return "<head:" + skull.getOwningPlayer().getUniqueId() + ">";
            }
        }
        final StringBuilder result = new StringBuilder(item.getType().getKey().toString());
        if (meta != null && meta.hasCustomModelData()) {
            result.append("<model-data:").append(meta.getCustomModelData()).append('>');
        }
        if (meta instanceof LeatherArmorMeta leather) {
            final var color = leather.getColor();
            result.append("<dye:").append(color.getRed()).append(',').append(color.getGreen())
                    .append(',').append(color.getBlue()).append('>');
        }
        if (meta instanceof BannerMeta banner && !banner.getPatterns().isEmpty()) {
            result.append("<banner:");
            for (int index = 0; index < banner.getPatterns().size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                final var pattern = banner.getPatterns().get(index);
                result.append(pattern.getColor().name()).append(' ')
                        .append(org.bukkit.Registry.BANNER_PATTERN.getKey(pattern.getPattern()).getKey().toUpperCase(java.util.Locale.ROOT));
            }
            result.append('>');
        }
        return result.toString();
    }
}
