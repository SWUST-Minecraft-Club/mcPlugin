package com.example.joinannounce;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 公告的实际发送：解析 MiniMessage 占位符，按 {@code announce.display} 走聊天栏和 / 或 dialog 弹窗，
 * 再补上可选的标题与音效。
 *
 * <p>所有发送方法都在玩家所属区域线程调用（进服延迟任务用 {@code player.getScheduler()}，
 * 菜单点击由 MainMenu 在查看者区域线程回调），不要从这里直接操作别的玩家。
 */
public final class AnnouncementService {

    /** 解析失败的文本只告警一次，避免菜单每秒重绘时刷屏。 */
    private final Set<String> warnedTexts = ConcurrentHashMap.newKeySet();
    private final JoinAnnouncePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnnouncementService(final JoinAnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    /** 进服公告：{@code joinContext} 为 true 时，首次进服的玩家追加 first-join 文案 */
    public void announce(final Player player, final boolean joinContext) {
        final AnnouncementSettings settings = this.plugin.settings();
        if (!settings.announceEnabled()) {
            return;
        }
        deliver(player, joinContext, settings.display());
    }

    /** 主菜单「服务器公告」点击：关掉菜单再发，避免弹窗被界面挡住 */
    public void showFromMenu(final Player player) {
        player.closeInventory();
        deliver(player, false, this.plugin.settings().display());
    }

    /** 立即给发送者预览一遍：玩家看聊天栏 + 弹窗 + 标题 + 音效，控制台只打印文本 */
    public void previewTo(final CommandSender sender) {
        final AnnouncementSettings settings = this.plugin.settings();
        if (sender instanceof Player player) {
            deliver(player, false, AnnouncementSettings.DisplayMode.BOTH);
            return;
        }
        sender.sendMessage(this.plugin.message("messages.preview-console",
                "<gray>以下为进服公告的文本内容（控制台不播放弹窗、标题与音效）：</gray>"));
        for (final Component line : lines(settings, null, false)) {
            sender.sendMessage(line);
        }
    }

    /**
     * 渲染一段配置文本。
     *
     * @param player 占位符上下文；{@code null} 表示没有具体玩家（控制台 / 菜单图标名称）
     */
    public Component render(final String raw, final Player player) {
        final TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("player", player == null ? "玩家" : player.getName()))
                .resolver(Placeholder.unparsed("online", Integer.toString(Bukkit.getOnlinePlayers().size())))
                .resolver(Placeholder.unparsed("max", Integer.toString(Bukkit.getMaxPlayers())))
                .resolver(Placeholder.unparsed("world", player == null ? "-" : player.getWorld().getName()))
                .build();
        try {
            return this.miniMessage.deserialize(raw, placeholders);
        } catch (final RuntimeException ex) {
            if (this.warnedTexts.add(raw)) {
                this.plugin.getComponentLogger().warn(Component.text(
                        "公告文本解析失败，已按纯文本显示：" + raw + "（" + ex.getMessage() + "）"));
            }
            return Component.text(raw);
        }
    }

    // ------------------------------------------------------------------ 内部

    private void deliver(
            final Player player, final boolean includeFirstJoin, final AnnouncementSettings.DisplayMode mode) {
        final AnnouncementSettings settings = this.plugin.settings();
        final List<Component> lines = lines(settings, player, includeFirstJoin);

        if (mode != AnnouncementSettings.DisplayMode.DIALOG) {
            for (final Component line : lines) {
                player.sendMessage(line);
            }
        }
        if (mode != AnnouncementSettings.DisplayMode.CHAT) {
            showDialog(player, settings, lines);
        }
        showTitle(player, settings.title());
        playSound(player, settings.sound());
    }

    private void showDialog(
            final Player player, final AnnouncementSettings settings, final List<Component> lines) {
        final AnnouncementSettings.DialogSettings dialog = settings.dialog();
        this.plugin.dialogs().show(player,
                render(dialog.title(), player),
                render(dialog.button(), null),
                lines,
                dialog);
    }

    private List<Component> lines(
            final AnnouncementSettings settings, final Player player, final boolean includeFirstJoin) {
        final List<Component> messages = new ArrayList<>();
        for (final String raw : settings.lines()) {
            if (raw != null && !raw.isBlank()) {
                messages.add(render(raw, player));
            }
        }
        if (includeFirstJoin && settings.firstJoinEnabled() && player != null && !player.hasPlayedBefore()) {
            for (final String raw : settings.firstJoinLines()) {
                if (raw != null && !raw.isBlank()) {
                    messages.add(render(raw, player));
                }
            }
        }
        return messages;
    }

    private void showTitle(final Player player, final AnnouncementSettings.TitleSettings title) {
        if (!title.enabled() || (title.title().isBlank() && title.subtitle().isBlank())) {
            return;
        }
        player.showTitle(Title.title(
                render(title.title(), player),
                render(title.subtitle(), player),
                Title.Times.times(
                        Duration.ofMillis(title.fadeInTicks() * 50L),
                        Duration.ofMillis(title.stayTicks() * 50L),
                        Duration.ofMillis(title.fadeOutTicks() * 50L))));
    }

    private void playSound(final Player player, final AnnouncementSettings.SoundSettings sound) {
        if (!sound.enabled() || sound.key() == null) {
            return;
        }
        player.playSound(Sound.sound(sound.key(), Sound.Source.PLAYER, sound.volume(), sound.pitch()));
    }
}
