package com.example.town.service;

import com.example.town.TownPlugin;
import com.example.town.util.Threads;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 聊天输入捕获：GUI 的「创建小镇」等需要玩家输入文字的场景，
 * 引导玩家在聊天栏输入；输入事件被吞掉不外发，超时自动取消。
 */
public final class ChatInputs implements Listener {

    public static final String CANCEL_WORD = "取消";

    private record Pending(long expireAt, Consumer<String> onInput) {
    }

    private final TownPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputs(TownPlugin plugin) {
        this.plugin = plugin;
    }

    /** 开始一次输入；期间玩家聊天不会外发。 */
    public void begin(Player player, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), new Pending(
                System.currentTimeMillis() + 30_000L, onInput));
        player.sendMessage(plugin.messages().render("chat-input-prompt"));
        Threads.globalLater(plugin, 600L, () -> {
            Pending waiting = pending.get(player.getUniqueId());
            if (waiting != null && waiting.expireAt() <= System.currentTimeMillis()) {
                pending.remove(player.getUniqueId());
                Threads.forPlayer(plugin, player, () ->
                        player.sendMessage(plugin.messages().render("chat-input-timeout")));
            }
        });
    }

    /** 主动取消（GUI 取消按钮 / /town cancel）。 */
    public void cancel(Player player) {
        if (pending.remove(player.getUniqueId()) != null) {
            player.sendMessage(plugin.messages().render("chat-input-cancelled"));
        }
    }

    public boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending waiting = pending.remove(event.getPlayer().getUniqueId());
        if (waiting == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        Player player = event.getPlayer();
        if (text.equals(CANCEL_WORD)) {
            Threads.forPlayer(plugin, player, () ->
                    player.sendMessage(plugin.messages().render("chat-input-cancelled")));
            return;
        }
        if (text.isEmpty()) {
            Threads.forPlayer(plugin, player, () ->
                    player.sendMessage(plugin.messages().render("chat-input-timeout")));
            return;
        }
        waiting.onInput().accept(text);
    }
}
