package com.example.joinannounce;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * 进服 / 退服事件：替换原版广播，并在进服后按配置延迟发送公告。
 *
 * <p>广播直接改写事件的 join / quit message（由服务端在合适的线程统一广播），
 * 不在事件里跨区域遍历在线玩家；延迟公告走 {@code player.getScheduler()}。
 */
public final class JoinQuitListener implements Listener {

    private final JoinAnnouncePlugin plugin;

    public JoinQuitListener(final JoinAnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final AnnouncementSettings settings = this.plugin.settings();

        this.plugin.resourcePacks().offer(player);

        if (settings.joinBroadcast().enabled()) {
            event.joinMessage(broadcastText(settings.joinBroadcast().message(), player));
        }

        if (!settings.announceEnabled()) {
            return;
        }

        if (settings.resourcePack().enabled()) {
            // 等资源包装载完成（或 10 秒兜底）再发公告，保证弹窗弹出时徽章贴图已就位
            this.plugin.resourcePacks().holdAnnouncement(player);
            final long fallbackTicks = Math.max(settings.delayTicks(), 200L);
            player.getScheduler().runDelayed(this.plugin,
                    task -> this.plugin.resourcePacks().flushIfWaiting(player),
                    null,
                    fallbackTicks);
        } else {
            player.getScheduler().runDelayed(this.plugin,
                    task -> this.plugin.announcements().announce(player, true),
                    null,
                    settings.delayTicks());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(final PlayerResourcePackStatusEvent event) {
        this.plugin.resourcePacks().onStatus(event.getPlayer(), event.getID(), event.getStatus());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.plugin.resourcePacks().discard(event.getPlayer());
        final AnnouncementSettings.BroadcastSettings quit = this.plugin.settings().quitBroadcast();
        if (quit.enabled()) {
            event.quitMessage(broadcastText(quit.message(), event.getPlayer()));
        }
    }

    /** 空文本表示不显示这条广播（返回 null 即取消原版消息） */
    private Component broadcastText(final String raw, final Player player) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return this.plugin.announcements().render(raw, player);
    }
}
