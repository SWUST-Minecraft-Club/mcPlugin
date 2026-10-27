package com.example.mainmenu.internal;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;

/** 自定义功能的默认指令和按键覆盖；显式空字符串表示禁用该键。 */
final class CustomFeatureCommands {
    private static final Map<String, ClickType> CLICK_TYPES = Map.of(
            "left", ClickType.LEFT,
            "right", ClickType.RIGHT,
            "shift-left", ClickType.SHIFT_LEFT,
            "shift-right", ClickType.SHIFT_RIGHT,
            "middle", ClickType.MIDDLE);

    private final String fallback;
    private final Map<ClickType, String> overrides;

    CustomFeatureCommands(final String fallback, final Map<ClickType, String> overrides) {
        this.fallback = normalize(fallback);
        final Map<ClickType, String> normalized = new EnumMap<>(ClickType.class);
        overrides.forEach((click, command) -> normalized.put(click, normalize(command)));
        this.overrides = Collections.unmodifiableMap(normalized);
    }

    static CustomFeatureCommands read(final ConfigurationSection feature, final Consumer<String> warning) {
        final Map<ClickType, String> overrides = new EnumMap<>(ClickType.class);
        final ConfigurationSection commands = feature.getConfigurationSection("commands");
        if (commands != null) {
            for (final String key : commands.getKeys(false)) {
                final ClickType click = CLICK_TYPES.get(key);
                if (click == null) {
                    warning.accept("未知点击类型 commands." + key
                            + "，支持 left / right / shift-left / shift-right / middle，已忽略");
                    continue;
                }
                final Object value = commands.get(key);
                if (value instanceof String command) {
                    overrides.put(click, command);
                } else {
                    warning.accept("commands." + key + " 必须是指令字符串，已禁用该点击");
                    overrides.put(click, "");
                }
            }
        } else if (feature.contains("commands")) {
            warning.accept("commands 必须是按点击类型配置的段落，已忽略");
        }
        return new CustomFeatureCommands(feature.getString("command", ""), overrides);
    }

    String fallback() {
        return fallback;
    }

    String command(final ClickType click) {
        return overrides.getOrDefault(click, fallback);
    }

    boolean clickable() {
        return !fallback.isBlank() || overrides.values().stream().anyMatch(command -> !command.isBlank());
    }

    Map<String, String> overridesForConfig() {
        final Map<String, String> result = new LinkedHashMap<>();
        overrides.forEach((click, command) ->
                result.put(click.name().toLowerCase(Locale.ROOT).replace('_', '-'), command));
        return result;
    }

    private static String normalize(final String command) {
        final String trimmed = command == null ? "" : command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }
}
