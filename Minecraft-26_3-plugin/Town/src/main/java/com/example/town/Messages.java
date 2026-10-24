package com.example.town;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * 消息渲染：统一加 prefix；配置缺失时给出可排查的占位文案。
 * 依赖主类的 volatile 配置快照，reload 后立即生效。
 */
public final class Messages {

    private final TownPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public Messages(TownPlugin plugin) {
        this.plugin = plugin;
    }

    /** 渲染带 prefix 的完整消息。 */
    public Component render(String key, TagResolver... resolvers) {
        String prefix = plugin.config().message("prefix");
        return mini.deserialize(prefix + plugin.config().message(key), resolvers);
    }

    /** 渲染不带 prefix 的消息（GUI 内使用）。 */
    public Component renderPlain(String key, TagResolver... resolvers) {
        return mini.deserialize(plugin.config().message(key), resolvers);
    }

    /** 原始 MiniMessage 字符串（不含 prefix），给 ArcMenu 文本渲染用。 */
    public String mini(String key) {
        return plugin.config().message(key);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(render(key, resolvers));
    }

    /** 直接发送一条临时拼的 MiniMessage（带 prefix）。 */
    public void sendRaw(CommandSender to, String miniMessage, TagResolver... resolvers) {
        String prefix = plugin.config().message("prefix");
        to.sendMessage(mini.deserialize(prefix + miniMessage, resolvers));
    }
}
