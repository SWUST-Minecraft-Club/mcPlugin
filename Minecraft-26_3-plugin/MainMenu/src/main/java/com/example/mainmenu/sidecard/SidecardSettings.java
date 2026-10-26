package com.example.mainmenu.sidecard;

import com.example.mainmenu.api.MenuSidecard.IconContext;
import com.example.mainmenu.api.MenuSidecard.Side;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

/** A validated immutable configuration; invalid reloads keep the previous snapshot. */
public record SidecardSettings(boolean enabled, Set<String> menus, SidecardLayout.Area area,
                               Palette palette, List<Definition> cards, Map<String, Override> overrides) {
    public record Palette(int background, int border, int hover, int text, int muted, int value, int track, int button) { }
    public record Row(String label, String value, String progress, String max) { }
    public record Button(String id, String label, Map<String, List<String>> actions) { }
    public record Definition(String id, boolean enabled, int order, Side side, double minHeight,
                             String permission, String requiredPlugin, String title, Material icon,
                             IconContext context, List<Row> rows, List<Button> buttons,
                             Map<String, List<String>> actions) { }
    public record Override(boolean enabled, Integer order, Side side, String permission) { }

    public SidecardSettings {
        menus = Set.copyOf(menus);
        cards = List.copyOf(cards);
        overrides = Map.copyOf(overrides);
    }

    public static SidecardSettings parse(final String source) throws InvalidConfigurationException {
        final var yaml = new YamlConfiguration();
        // Preserve dots and slashes in quoted API ids while Bukkit converts maps to sections.
        yaml.options().pathSeparator('\u001f');
        yaml.loadFromString(source);
        yaml.options().pathSeparator('.');
        return read(yaml);
    }

    public static SidecardSettings read(final YamlConfiguration yaml) {
        final var defaults = SidecardLayout.Area.defaults();
        final var area = new SidecardLayout.Area(
                yaml.getDouble("layout.left-x", defaults.leftX()), yaml.getDouble("layout.right-x", defaults.rightX()),
                yaml.getDouble("layout.width", defaults.width()), yaml.getDouble("layout.top", defaults.top()),
                yaml.getDouble("layout.height", defaults.height()), yaml.getDouble("layout.gap", defaults.gap()),
                yaml.getInt("layout.max-per-column", defaults.maxPerColumn()));
        final Palette palette = new Palette(color(yaml, "background", "#173B3C"), color(yaml, "border", "#79C7B5"),
                color(yaml, "hover", "#C7F1DE"), color(yaml, "text", "#EDF5E9"), color(yaml, "muted", "#B5D5CB"),
                color(yaml, "value", "#ECD18B"), color(yaml, "track", "#315455"), color(yaml, "button", "#285753"));
        final List<Definition> cards = new ArrayList<>();
        final ConfigurationSection definitions = yaml.getConfigurationSection("cards");
        if (definitions != null) for (final String key : definitions.getKeys(false)) {
            if (!key.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid card key: " + key);
            final ConfigurationSection card = definitions.getConfigurationSection(key);
            if (card == null) throw new IllegalArgumentException("Card must be a mapping: " + key);
            final String id = "yaml:" + key;
            final String material = card.getString("icon.material", "PAPER");
            final Material icon = material.equalsIgnoreCase("none") ? null : Material.matchMaterial(material);
            if (icon == null && !material.equalsIgnoreCase("none") || icon == Material.AIR
                    || icon == Material.CAVE_AIR || icon == Material.VOID_AIR) {
                throw new IllegalArgumentException("Invalid card icon: " + key);
            }
            final List<Row> rows = new ArrayList<>();
            for (final Map<?, ?> row : card.getMapList("rows")) {
                rows.add(new Row(string(row, "label", ""), string(row, "value", ""),
                        string(row, "progress", ""), string(row, "max", "1")));
            }
            final List<Button> buttons = new ArrayList<>();
            for (final Map<?, ?> button : card.getMapList("buttons")) {
                final String buttonId = string(button, "id", "");
                new com.example.mainmenu.api.MenuSidecard.Button(buttonId, "");
                buttons.add(new Button(buttonId, string(button, "label", buttonId), actions(button.get("actions"))));
            }
            final double minHeight = card.getDouble("min-height", 88);
            if (!Double.isFinite(minHeight) || minHeight < 48 || minHeight > area.height()
                    || rows.size() > 8 || buttons.size() > 2
                    || buttons.stream().map(Button::id).distinct().count() != buttons.size()) {
                throw new IllegalArgumentException("Card too tall or too many rows/buttons: " + key);
            }
            cards.add(new Definition(id, card.getBoolean("enabled", true), card.getInt("order", 0),
                    side(card.getString("side", "auto")), minHeight, card.getString("permission", ""),
                    card.getString("requires-plugin", ""), card.getString("title", key), icon,
                    IconContext.valueOf(card.getString("icon.context", "FIXED").toUpperCase(Locale.ROOT)),
                    List.copyOf(rows), List.copyOf(buttons), actions(card.get("actions"))));
        }
        final Map<String, Override> overrides = new LinkedHashMap<>();
        final ConfigurationSection section = yaml.getConfigurationSection("overrides");
        if (section != null) for (final var entry : section.getValues(false).entrySet()) {
            final String id = entry.getKey();
            if (!(entry.getValue() instanceof ConfigurationSection value)) throw new IllegalArgumentException("Override must be a mapping: " + id);
            overrides.put(id, new Override(value.getBoolean("enabled", true),
                    value.contains("order") ? value.getInt("order") : null,
                    value.contains("side") ? side(value.getString("side")) : null, value.getString("permission", "")));
        }
        final List<String> menus = yaml.contains("menus") ? yaml.getStringList("menus")
                : List.of("home", "mainmenu-features", "mainmenu-profile", "mainmenu-economy");
        if (menus.stream().anyMatch(id -> !id.matches("[a-z0-9][a-z0-9_-]*"))) {
            throw new IllegalArgumentException("Invalid sidecard menu id");
        }
        return new SidecardSettings(yaml.getBoolean("enabled", false), Set.copyOf(menus), area, palette, cards, overrides);
    }

    private static int color(final YamlConfiguration yaml, final String key, final String fallback) {
        final String value = yaml.getString("theme." + key, fallback);
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid theme color: " + key);
        return Integer.parseInt(value.substring(1), 16);
    }

    private static Side side(final String value) { return Side.valueOf(value.toUpperCase(Locale.ROOT)); }
    private static String string(final Map<?, ?> values, final String key, final String fallback) {
        return values.get(key) == null ? fallback : String.valueOf(values.get(key));
    }

    private static Map<String, List<String>> actions(final Object source) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        if (source instanceof ConfigurationSection section) return actions(section.getValues(false));
        if (source instanceof List<?> lines) result.put("all", lines.stream().map(String::valueOf).toList());
        else if (source instanceof Map<?, ?> map) for (final var e : map.entrySet()) {
            final String trigger = String.valueOf(e.getKey());
            if (!Set.of("all", "left", "right", "shift-left", "shift-right", "middle").contains(trigger)) {
                throw new IllegalArgumentException("Unknown sidecard click: " + trigger);
            }
            if (!(e.getValue() instanceof List<?> lines)) throw new IllegalArgumentException("Actions must be a list");
            result.put(trigger, lines.stream().map(String::valueOf).toList());
        }
        else if (source != null) throw new IllegalArgumentException("Actions must be a mapping or a list");
        for (final String action : result.values().stream().flatMap(List::stream).toList()) {
            if (!action.equals("close") && !action.matches("(?s)(route|feature|menu|player|message): .+")) {
                throw new IllegalArgumentException("Unknown sidecard action: " + action);
            }
            if (action.indexOf('\n') >= 0 || action.indexOf('\r') >= 0) throw new IllegalArgumentException("Multiline action");
        }
        return Map.copyOf(result);
    }
}
