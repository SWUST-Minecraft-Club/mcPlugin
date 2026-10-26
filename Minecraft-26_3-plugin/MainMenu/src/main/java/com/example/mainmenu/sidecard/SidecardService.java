package com.example.mainmenu.sidecard;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.MenuSidecard;
import com.example.mainmenu.api.MenuSidecard.Content;
import com.example.mainmenu.api.MenuSidecard.Row;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** YAML and API cards share one filtering and layout path. No ArcMenu classes cross this boundary. */
public final class SidecardService {
    public record Rendered(String id, int order, MenuSidecard.Side side, double height, Content content,
                           MenuSidecard source, SidecardSettings.Definition yaml) {
        public SidecardLayout.Card layout() { return new SidecardLayout.Card(id, order, side, height); }
        /** Content values are deliberately absent: a changed score never moves a card. */
        public String structure() {
            return id + '|' + order + '|' + side + '|' + height + '|' + content.icon() + '|' + content.iconContext()
                    + '|' + content.rows().stream().map(row -> row.progress() >= 0).toList()
                    + '|' + clickable() + '|' + content.buttons().stream()
                    .map(button -> button.id() + ':' + clickable(button.id())).toList();
        }
        public boolean clickable() { return clickable("card"); }
        public boolean clickable(final String action) {
            if (source != null) return source.clickable();
            final Map<String, List<String>> actions = action.equals("card") ? yaml.actions()
                    : yaml.buttons().stream().filter(button -> button.id().equals(action))
                    .findFirst().map(SidecardSettings.Button::actions).orElse(Map.of());
            return actions.values().stream().anyMatch(lines -> !lines.isEmpty());
        }
    }
    private final MainMenuPlugin plugin;
    private final SidecardRegistry registry;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private volatile SidecardSettings settings = SidecardSettings.read(new YamlConfiguration());

    public SidecardService(final MainMenuPlugin plugin, final SidecardRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public SidecardSettings settings() { return settings; }

    public void reload() throws IOException {
        final Path target = plugin.getDataFolder().toPath().resolve("sidecards.yml");
        if (!Files.exists(target)) plugin.saveResource("sidecards.yml", false);
        try {
            settings = SidecardSettings.parse(Files.readString(target));
            reported.clear();
        } catch (final InvalidConfigurationException | IllegalArgumentException e) {
            throw new IOException("sidecards.yml: " + e.getMessage(), e);
        }
    }

    public List<Rendered> snapshot(final Player viewer, final UnaryOperator<String> resolver) {
        final SidecardSettings config = settings;
        if (!config.enabled() || !viewer.hasPermission("mainmenu.use")) return List.of();
        final List<Rendered> cards = new ArrayList<>();
        for (final var definition : config.cards()) {
            if (!definition.enabled() || !permission(viewer, definition.permission())
                    || !definition.requiredPlugin().isBlank() && !Bukkit.getPluginManager().isPluginEnabled(definition.requiredPlugin())) continue;
            try {
                final Content content = new Content(text(definition.title(), viewer, resolver), definition.icon(), definition.context(),
                        definition.rows().stream().map(row -> new Row(text(row.label(), viewer, resolver), text(row.value(), viewer, resolver),
                                row.progress().isBlank() ? -1 : fraction(row, viewer, resolver))).toList(),
                        definition.buttons().stream().map(button -> new MenuSidecard.Button(button.id(), text(button.label(), viewer, resolver))).toList());
                add(cards, new Rendered(definition.id(), definition.order(), definition.side(),
                        height(content, definition.minHeight()), content, null, definition), config);
            } catch (final RuntimeException e) { report(definition.id(), e); }
        }
        for (final MenuSidecard card : registry.cards()) {
            try {
                final var override = config.overrides().get(card.id());
                if (override != null && (!override.enabled() || !permission(viewer, override.permission())) || !card.visible(viewer)) continue;
                final Content content = card.content(viewer);
                if (content == null) continue;
                add(cards, new Rendered(card.id(), override != null && override.order() != null ? override.order() : card.order(),
                        override != null && override.side() != null ? override.side() : card.side(), height(content, card.minHeight()),
                        content, card, null), config);
            } catch (final RuntimeException e) { report(card.id(), e); }
        }
        return cards.stream().sorted(Comparator.comparingInt(Rendered::order).thenComparing(Rendered::id)).toList();
    }

    private void add(final List<Rendered> cards, final Rendered card, final SidecardSettings config) {
        if (card.content().icon() != null && !card.content().icon().isItem()) {
            report(card.id(), new IllegalArgumentException("Icon is not an inventory item"));
            return;
        }
        if (card.height() > config.area().height()) {
            report(card.id(), new IllegalArgumentException("Content exceeds column height; use a detail menu"));
        } else cards.add(card);
    }

    public static double height(final Content content, final double minimum) {
        return Math.max(minimum, 48 + content.rows().stream().mapToInt(row -> row.progress() >= 0 ? 32 : 20).sum()
                + (content.buttons().isEmpty() ? 0 : 30) + 12);
    }

    private double fraction(final SidecardSettings.Row row, final Player viewer, final UnaryOperator<String> resolver) {
        try {
            final double amount = Double.parseDouble(expand(row.progress(), viewer, resolver));
            final double max = Double.parseDouble(expand(row.max(), viewer, resolver));
            return Double.isFinite(amount) && Double.isFinite(max) && max > 0 ? Math.clamp(amount / max, 0, 1) : 0;
        } catch (final NumberFormatException e) { return 0; }
    }

    private net.kyori.adventure.text.Component text(final String value, final Player viewer, final UnaryOperator<String> resolver) {
        return plugin.miniRaw(expand(value, viewer, resolver));
    }

    public String expand(final String input, final Player viewer, final UnaryOperator<String> resolver) {
        String value = resolver.apply(input.replace("{player}", viewer.getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size())));
        if (value.indexOf('%') >= 0) {
            final var papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi != null && papi.isEnabled()) try {
                final Class<?> type = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
                final Method method = type.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                value = String.valueOf(method.invoke(null, viewer, value));
            } catch (final ReflectiveOperationException | LinkageError e) { report("PlaceholderAPI", e); }
            value = value.replaceAll("%[A-Za-z0-9_:.\\-]+%", "--");
        }
        return value;
    }

    public static List<String> actions(final Rendered card, final String action, final ClickType click) {
        if (card.yaml() == null) return List.of();
        Map<String, List<String>> actions = card.yaml().actions();
        if (!action.equals("card")) actions = card.yaml().buttons().stream().filter(b -> b.id().equals(action))
                .findFirst().map(SidecardSettings.Button::actions).orElse(Map.of());
        final String key = click.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return actions.getOrDefault(key, actions.getOrDefault("all", List.of()));
    }

    private static boolean permission(final Player viewer, final String node) { return node.isBlank() || viewer.hasPermission(node); }
    private void report(final String id, final Throwable error) {
        if (reported.add(id)) plugin.getLogger().warning("侧卡 " + id + " 已跳过或使用占位值：" + error);
    }
}
