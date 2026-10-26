package com.example.joinannounce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml 的不可变快照。
 *
 * <p>Folia 里不同玩家的进服事件可能落在不同区域线程，所以配置在
 * {@code /joinannounce reload} 时整体替换，运行期不再访问 {@link FileConfiguration}。
 */
public final class AnnouncementSettings {

    private final boolean announceEnabled;
    private final DisplayMode display;
    private final int delayTicks;
    private final boolean oncePerDay;
    private final int dayZoneOffsetHours;
    private final List<String> lines;
    private final boolean firstJoinEnabled;
    private final List<String> firstJoinLines;
    private final TitleSettings title;
    private final SoundSettings sound;
    private final DialogSettings dialog;
    private final BroadcastSettings joinBroadcast;
    private final BroadcastSettings quitBroadcast;
    private final MenuSettings menu;

    private AnnouncementSettings(
            final boolean announceEnabled,
            final DisplayMode display,
            final int delayTicks,
            final boolean oncePerDay,
            final int dayZoneOffsetHours,
            final List<String> lines,
            final boolean firstJoinEnabled,
            final List<String> firstJoinLines,
            final TitleSettings title,
            final SoundSettings sound,
            final DialogSettings dialog,
            final BroadcastSettings joinBroadcast,
            final BroadcastSettings quitBroadcast,
            final MenuSettings menu) {
        this.announceEnabled = announceEnabled;
        this.display = display;
        this.delayTicks = delayTicks;
        this.oncePerDay = oncePerDay;
        this.dayZoneOffsetHours = dayZoneOffsetHours;
        this.lines = lines;
        this.firstJoinEnabled = firstJoinEnabled;
        this.firstJoinLines = firstJoinLines;
        this.title = title;
        this.sound = sound;
        this.dialog = dialog;
        this.joinBroadcast = joinBroadcast;
        this.quitBroadcast = quitBroadcast;
        this.menu = menu;
    }

    public static AnnouncementSettings load(final FileConfiguration config, final ComponentLogger logger) {
        final boolean announceEnabled = config.getBoolean("announce.enabled", true);
        final List<String> lines = strings(config, "announce.lines");
        if (announceEnabled && lines.isEmpty()) {
            logger.warn(Component.text("announce.lines 为空：进服后不会发送公告内容"));
        }

        final boolean firstJoinEnabled = config.getBoolean("first-join.enabled", true);
        final List<String> firstJoinLines = strings(config, "first-join.lines");

        final TitleSettings title = new TitleSettings(
                config.getBoolean("announce.title.enabled", false),
                text(config, "announce.title.title", ""),
                text(config, "announce.title.subtitle", ""),
                ticks(config, "announce.title.fade-in-ticks", 10),
                ticks(config, "announce.title.stay-ticks", 60),
                ticks(config, "announce.title.fade-out-ticks", 20));

        SoundSettings sound = new SoundSettings(false, null, 1.0F, 1.0F);
        if (config.getBoolean("announce.sound.enabled", false)) {
            final String rawKey = text(config, "announce.sound.key", "minecraft:entity.player.levelup");
            Key key = null;
            try {
                key = Key.key(rawKey);
            } catch (final RuntimeException ex) {
                logger.warn(Component.text("announce.sound.key 无效（需 <命名空间>:<键>）：" + rawKey + "，已跳过音效"));
            }
            if (key != null) {
                sound = new SoundSettings(
                        true,
                        key,
                        ranged(config, "announce.sound.volume", 1.0D, 0.0D, 1.0D),
                        ranged(config, "announce.sound.pitch", 1.0D, 0.5D, 2.0D));
            }
        }

        final DialogSettings dialog = new DialogSettings(
                text(config, "announce.dialog.title", "<gold>服务器公告</gold>"),
                (int) ranged(config, "announce.dialog.width", 260.0D, 60.0D, 600.0D),
                text(config, "announce.dialog.button", "知道了"),
                config.getBoolean("announce.dialog.can-close-with-escape", true));

        final BroadcastSettings joinBroadcast = broadcast(config, logger, "broadcast.join", true);
        final BroadcastSettings quitBroadcast = broadcast(config, logger, "broadcast.quit", true);

        final MenuSettings menu = new MenuSettings(
                config.getBoolean("menu.enabled", true),
                material(config, logger),
                text(config, "menu.name", "<gold>服务器公告</gold>"),
                strings(config, "menu.lines"),
                config.getInt("menu.order", 20));

        return new AnnouncementSettings(announceEnabled, displayMode(config, logger),
                Math.max(0, config.getInt("announce.delay-ticks", 20)),
                config.getBoolean("announce.once-per-day", false),
                Math.max(-18, Math.min(18, config.getInt("announce.day-zone-offset-hours", 8))),
                lines, firstJoinEnabled, firstJoinLines, title, sound, dialog, joinBroadcast, quitBroadcast, menu);
    }

    public boolean announceEnabled() {
        return this.announceEnabled;
    }

    public DisplayMode display() {
        return this.display;
    }

    public int delayTicks() {
        return this.delayTicks;
    }

    public boolean oncePerDay() {
        return this.oncePerDay;
    }

    public int dayZoneOffsetHours() {
        return this.dayZoneOffsetHours;
    }

    public List<String> lines() {
        return this.lines;
    }

    public boolean firstJoinEnabled() {
        return this.firstJoinEnabled;
    }

    public List<String> firstJoinLines() {
        return this.firstJoinLines;
    }

    public TitleSettings title() {
        return this.title;
    }

    public SoundSettings sound() {
        return this.sound;
    }

    public DialogSettings dialog() {
        return this.dialog;
    }

    public BroadcastSettings joinBroadcast() {
        return this.joinBroadcast;
    }

    public BroadcastSettings quitBroadcast() {
        return this.quitBroadcast;
    }

    public MenuSettings menu() {
        return this.menu;
    }

    // ------------------------------------------------------------------ 读取辅助

    private static DisplayMode displayMode(final FileConfiguration config, final ComponentLogger logger) {
        final String raw = text(config, "announce.display", "dialog").trim().toLowerCase(Locale.ROOT);
        switch (raw) {
            case "chat":
                return DisplayMode.CHAT;
            case "both":
                return DisplayMode.BOTH;
            case "dialog":
                return DisplayMode.DIALOG;
            default:
                logger.warn(Component.text(
                        "announce.display 无效：" + raw + "（可用 dialog / chat / both），已按 dialog 处理"));
                return DisplayMode.DIALOG;
        }
    }

    private static BroadcastSettings broadcast(
            final FileConfiguration config, final ComponentLogger logger, final String path, final boolean fallback) {
        final boolean enabled = config.getBoolean(path + ".enabled", fallback);
        final String message = text(config, path + ".message", "");
        if (enabled && message.isBlank()) {
            logger.warn(Component.text(path + ".message 为空：该条广播不会显示"));
        }
        return new BroadcastSettings(enabled, message);
    }

    private static Material material(final FileConfiguration config, final ComponentLogger logger) {
        final String name = text(config, "menu.material", "WRITTEN_BOOK");
        final Material material = Material.matchMaterial(name);
        if (material == null || material.isAir()) {
            logger.warn(Component.text("menu.material 无效：" + name + "，已回退 WRITTEN_BOOK"));
            return Material.WRITTEN_BOOK;
        }
        return material;
    }

    private static String text(final FileConfiguration config, final String path, final String fallback) {
        final String value = config.getString(path, fallback);
        return value == null ? fallback : value;
    }

    private static List<String> strings(final FileConfiguration config, final String path) {
        final List<?> raw = config.getList(path);
        if (raw == null) {
            return List.of();
        }
        final List<String> result = new ArrayList<>(raw.size());
        for (final Object item : raw) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }

    private static int ticks(final FileConfiguration config, final String path, final int fallback) {
        return Math.max(0, config.getInt(path, fallback));
    }

    private static float ranged(
            final FileConfiguration config, final String path, final double fallback,
            final double min, final double max) {
        final double value = config.getDouble(path, fallback);
        return (float) Math.min(max, Math.max(min, value));
    }

    // ------------------------------------------------------------------ 值对象

    /** 公告显示通道：dialog 只弹窗、chat 只发聊天栏、both 两者都来 */
    public enum DisplayMode {
        DIALOG,
        CHAT,
        BOTH
    }

    /** dialog 弹窗 */
    public record DialogSettings(String title, int width, String button, boolean canCloseWithEscape) {
    }

    /** 标题与淡入 / 停留 / 淡出时长（tick） */
    public record TitleSettings(
            boolean enabled, String title, String subtitle,
            int fadeInTicks, int stayTicks, int fadeOutTicks) {
    }

    /** 提示音；{@code key} 为 {@code null} 表示音效键无效、已禁用 */
    public record SoundSettings(boolean enabled, Key key, float volume, float pitch) {
    }

    /** 进服 / 退服广播；{@code message} 为空表示不显示这一条 */
    public record BroadcastSettings(boolean enabled, String message) {
    }

    /** 主菜单入口 */
    public record MenuSettings(
            boolean enabled, Material material, String name, List<String> lines, int order) {
    }
}
