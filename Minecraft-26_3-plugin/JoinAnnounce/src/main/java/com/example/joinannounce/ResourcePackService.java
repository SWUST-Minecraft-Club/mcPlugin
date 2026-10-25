package com.example.joinannounce;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * 进服时向玩家下发资源包（社团徽章等界面素材），并等它装载完再触发进服公告。
 *
 * <p>Minecraft 协议只传「URL + SHA-1」，资源包本体需要放在玩家能访问的 http/https 地址；
 * 带 SHA-1 时客户端按 hash 缓存，之后进服不会重复下载。{@code force: true} 表示玩家必须接受才能进服。
 *
 * <p>公告时序：启用了资源包时，公告压到 {@link PlayerResourcePackStatusEvent} 的
 * 终态（装载成功 / 拒绝 / 失败）或 10 秒兜底超时之后才发，避免弹窗出现时贴图还没就位。
 * 等待集合跨区域线程访问（进服、状态事件、超时任务可能落在不同线程），用并发容器。
 */
final class ResourcePackService {

    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    private final JoinAnnouncePlugin plugin;

    ResourcePackService(final JoinAnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    /** 未启用 / URL 无效时静默跳过 */
    void offer(final Player player) {
        final AnnouncementSettings.ResourcePackSettings pack = this.plugin.settings().resourcePack();
        if (!pack.enabled() || pack.url() == null) {
            return;
        }
        final Component prompt = this.plugin.announcements().render(pack.prompt(), player);
        player.setResourcePack(pack.packId(), pack.url().toString(), pack.sha1(), prompt, pack.force());
    }

    /** 标记该玩家的公告等资源包终态 */
    void holdAnnouncement(final Player player) {
        this.waiting.add(player.getUniqueId());
    }

    /** 资源包状态回调：终态时放行公告；中间状态（ACCEPTED / DOWNLOADED）继续等 */
    void onStatus(final Player player, final UUID packId, final PlayerResourcePackStatusEvent.Status status) {
        final AnnouncementSettings.ResourcePackSettings pack = this.plugin.settings().resourcePack();
        if (pack.packId() == null || !pack.packId().equals(packId)) {
            return;
        }
        final boolean settled = switch (status) {
            case SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
            default -> false;
        };
        if (settled && this.waiting.remove(player.getUniqueId())) {
            this.plugin.announcements().announce(player, true);
        }
    }

    /** 兜底：超时后仍在等待就直接发公告（资源包没就位时弹窗里徽章是默认贴图） */
    void flushIfWaiting(final Player player) {
        if (this.waiting.remove(player.getUniqueId())) {
            this.plugin.announcements().announce(player, true);
        }
    }

    /** 退服清理，防止等待集合泄漏 */
    void discard(final Player player) {
        this.waiting.remove(player.getUniqueId());
    }
}
