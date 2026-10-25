package com.example.chatprivacy;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 把玩家公开聊天改写为无签名的系统消息。
 *
 * <p>取消原版广播后，签名聊天包不会再发给任何客户端；改发的系统消息没有签名，
 * 因此客户端无法把它作为聊天举报的证据。聊天格式仍由 Paper 的 {@link ChatRenderer} 渲染，
 * 兼容其它聊天格式化插件。
 */
public final class ChatRewriteListener implements Listener {

    private final ChatPrivacyPlugin plugin;
    private final AtomicBoolean renderWarningLogged = new AtomicBoolean();

    public ChatRewriteListener(final ChatPrivacyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(final AsyncChatEvent event) {
        final ChatPrivacySettings settings = this.plugin.settings();
        if (!settings.enabled() || event.isCancelled()) {
            // 关闭时不接管；已被其它插件取消（禁言等）时尊重原结果
            return;
        }

        final Player sender = event.getPlayer();
        if (settings.bedrockOnly() && sender.getUniqueId().version() != 0) {
            // UUID version 0 是 Geyser 转发基岩版玩家的特征
            return;
        }

        // 先取消原版广播：原始签名聊天包不会再发给任何客户端
        event.setCancelled(true);

        final List<Audience> viewers = new ArrayList<>(event.viewers());
        final ChatRenderer renderer = event.renderer();
        final Component displayName = sender.displayName();
        final Component message = event.message();
        final boolean signed = event.signedMessage() != null
                && event.signedMessage().signature() != null;
        this.plugin.recordRewritten(signed);

        boolean consoleIncluded = false;
        for (final Audience viewer : viewers) {
            final Component rendered = this.render(renderer, sender, displayName, message, viewer);
            if (viewer instanceof ConsoleCommandSender) {
                consoleIncluded = true;
                if (settings.logChat()) {
                    viewer.sendMessage(rendered);
                }
                continue;
            }
            if (viewer instanceof Player player) {
                // Folia：玩家消息必须在该玩家所属区域线程发送
                player.getScheduler().run(this.plugin, task -> player.sendMessage(rendered), null);
            } else {
                // 控制台之外的非玩家 Audience（例如聊天桥接插件）自行处理线程
                viewer.sendMessage(rendered);
            }
        }

        if (settings.logChat() && !consoleIncluded) {
            final ConsoleCommandSender console = Bukkit.getConsoleSender();
            console.sendMessage(this.render(renderer, sender, displayName, message, console));
        }
    }

    private Component render(final ChatRenderer renderer, final Player sender,
                             final Component displayName, final Component message,
                             final Audience viewer) {
        try {
            return renderer.render(sender, displayName, message, viewer);
        } catch (final RuntimeException ex) {
            if (this.renderWarningLogged.compareAndSet(false, true)) {
                this.plugin.getSLF4JLogger().warn(
                        "聊天渲染器抛出异常，后续消息使用默认格式「<玩家名> 消息」", ex);
            }
            return Component.text().append(displayName).append(Component.text(" ")).append(message).build();
        }
    }
}
