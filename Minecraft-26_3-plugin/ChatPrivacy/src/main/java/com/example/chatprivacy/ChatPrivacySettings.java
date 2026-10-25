package com.example.chatprivacy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml 的不可变快照。重载时整体替换，跨区域线程读取。
 */
public record ChatPrivacySettings(
        boolean enabled,
        boolean bedrockOnly,
        boolean logChat,
        boolean warnOnSecureProfile,
        String prefixRaw,
        String reloadedRaw
) {

    private static final String DEFAULT_PREFIX = "<gray>[<aqua>聊天隐私</aqua>]</gray> ";
    private static final String DEFAULT_RELOADED = "<green>配置已重新加载。</green>";

    public static ChatPrivacySettings load(final FileConfiguration config) {
        return new ChatPrivacySettings(
                config.getBoolean("enabled", true),
                config.getBoolean("bedrock-only", false),
                config.getBoolean("log-chat", true),
                config.getBoolean("warn-on-secure-profile", true),
                text(config, "messages.prefix", DEFAULT_PREFIX),
                text(config, "messages.reloaded", DEFAULT_RELOADED)
        );
    }

    public Component prefix() {
        return parse(this.prefixRaw, "[聊天隐私] ", NamedTextColor.GRAY);
    }

    public Component prefixed(final String raw) {
        return this.prefix().append(parse(raw, raw, NamedTextColor.WHITE));
    }

    public Component reloaded() {
        return this.prefixed(this.reloadedRaw);
    }

    private static String text(final FileConfiguration config, final String key, final String fallback) {
        final String value = config.getString(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Component parse(final String raw, final String fallback, final NamedTextColor fallbackColor) {
        try {
            return MiniMessage.miniMessage().deserialize(raw);
        } catch (final RuntimeException ex) {
            // 配置写错时不阻断聊天，退化成纯文本
            return Component.text(fallback, fallbackColor);
        }
    }
}
