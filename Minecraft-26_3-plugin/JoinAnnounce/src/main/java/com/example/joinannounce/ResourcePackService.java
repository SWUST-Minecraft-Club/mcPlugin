package com.example.joinannounce;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 进服时向玩家下发资源包（社团徽章等界面素材）。
 *
 * <p>Minecraft 协议只传「URL + SHA-1」，资源包本体需要放在玩家能访问的 http/https 地址；
 * 带 SHA-1 时客户端按 hash 缓存，之后进服不会重复下载。{@code force: true} 表示玩家必须接受才能进服。
 */
final class ResourcePackService {

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
}
