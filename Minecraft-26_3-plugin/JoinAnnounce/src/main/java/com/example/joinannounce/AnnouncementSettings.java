package com.example.joinannounce;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
    private final List<String> lines;
    private final boolean firstJoinEnabled;
    private final List<String> firstJoinLines;
    private final TitleSettings title;
    private final SoundSettings sound;
    private final DialogSettings dialog;
    private final BroadcastSettings joinBroadcast;
    private final BroadcastSettings quitBroadcast;
    private final MenuSettings menu;
    private final ResourcePackSettings resourcePack;

    private AnnouncementSettings(
            final boolean announceEnabled,
            final DisplayMode display,
            final int delayTicks,
            final List<String> lines,
            final boolean firstJoinEnabled,
            final List<String> firstJoinLines,
            final TitleSettings title,
            final SoundSettings sound,
            final DialogSettings dialog,
            final BroadcastSettings joinBroadcast,
            final BroadcastSettings quitBroadcast,
            final MenuSettings menu,
            final ResourcePackSettings resourcePack) {
        this.announceEnabled = announceEnabled;
        this.display = display;
        this.delayTicks = delayTicks;
        this.lines = lines;
        this.firstJoinEnabled = firstJoinEnabled;
        this.firstJoinLines = firstJoinLines;
        this.title = title;
        this.sound = sound;
        this.dialog = dialog;
        this.joinBroadcast = joinBroadcast;
        this.quitBroadcast = quitBroadcast;
        this.menu = menu;
        this.resourcePack = resourcePack;
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

        final DialogSettings dialog = dialog(config, logger);
        final ResourcePackSettings resourcePack = resourcePack(config, logger);
        if (dialog.iconSource() == IconSource.PACK && !resourcePack.enabled()) {
            logger.warn(Component.text(
                    "announce.dialog.icon.source 为 pack，但 resource-pack 未启用：弹窗图标会是没有贴图的物品"));
        }

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
                lines, firstJoinEnabled, firstJoinLines, title, sound, dialog, joinBroadcast, quitBroadcast, menu,
                resourcePack);
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

    public ResourcePackSettings resourcePack() {
        return this.resourcePack;
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

    private static DialogSettings dialog(final FileConfiguration config, final ComponentLogger logger) {
        final IconSource source = iconSource(config, logger);
        boolean iconEnabled = config.getBoolean("announce.dialog.icon.enabled", false);
        URL iconUrl = null;
        if (iconEnabled && source == IconSource.HEAD) {
            final String rawUrl = text(config, "announce.dialog.icon.texture-url", "").trim();
            if (rawUrl.isEmpty()) {
                logger.warn(Component.text("announce.dialog.icon.enabled 已开启但 texture-url 为空：弹窗不放徽章图标"));
                iconEnabled = false;
            } else {
                try {
                    final URI uri = new URI(rawUrl);
                    final String scheme = uri.getScheme();
                    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        logger.warn(Component.text(
                                "announce.dialog.icon.texture-url 需要是 http/https 地址：" + rawUrl));
                        iconEnabled = false;
                    } else {
                        iconUrl = uri.toURL();
                    }
                } catch (final URISyntaxException | MalformedURLException ex) {
                    logger.warn(Component.text("announce.dialog.icon.texture-url 无效："
                            + rawUrl + "（" + ex.getMessage() + "）"));
                    iconEnabled = false;
                }
            }
        }
        return new DialogSettings(
                text(config, "announce.dialog.title", "<gold>服务器公告</gold>"),
                (int) ranged(config, "announce.dialog.width", 260.0D, 60.0D, 600.0D),
                text(config, "announce.dialog.button", "知道了"),
                config.getBoolean("announce.dialog.can-close-with-escape", true),
                iconEnabled,
                iconUrl,
                (int) ranged(config, "announce.dialog.icon.size", 48.0D, 8.0D, 256.0D),
                iconSource(config, logger));
    }

    private static IconSource iconSource(final FileConfiguration config, final ComponentLogger logger) {
        final String raw = text(config, "announce.dialog.icon.source", "head").trim().toLowerCase(Locale.ROOT);
        switch (raw) {
            case "pack":
                return IconSource.PACK;
            case "head":
                return IconSource.HEAD;
            default:
                logger.warn(Component.text(
                        "announce.dialog.icon.source 无效：" + raw + "（可用 head / pack），已按 head 处理"));
                return IconSource.HEAD;
        }
    }

    private static ResourcePackSettings resourcePack(final FileConfiguration config, final ComponentLogger logger) {
        boolean enabled = config.getBoolean("resource-pack.enabled", false);
        final String rawUrl = text(config, "resource-pack.url", "").trim();
        URL url = null;
        if (enabled) {
            if (rawUrl.isEmpty()) {
                logger.warn(Component.text("resource-pack.enabled 已开启但 url 为空：不下发资源包"));
                enabled = false;
            } else {
                try {
                    final URI uri = new URI(rawUrl);
                    final String scheme = uri.getScheme();
                    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        logger.warn(Component.text("resource-pack.url 需要是 http/https 地址：" + rawUrl));
                        enabled = false;
                    } else {
                        url = uri.toURL();
                    }
                } catch (final URISyntaxException | MalformedURLException ex) {
                    logger.warn(Component.text("resource-pack.url 无效：" + rawUrl + "（" + ex.getMessage() + "）"));
                    enabled = false;
                }
            }
        }

        byte[] sha1 = null;
        final String rawSha1 = text(config, "resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (!rawSha1.isEmpty()) {
            if (rawSha1.matches("[0-9a-f]{40}")) {
                sha1 = new byte[20];
                for (int i = 0; i < 20; i++) {
                    sha1[i] = (byte) Integer.parseInt(rawSha1.substring(i * 2, i * 2 + 2), 16);
                }
            } else {
                logger.warn(Component.text("resource-pack.sha1 需要 40 位十六进制（SHA-1），已忽略：" + rawSha1));
            }
        }

        return new ResourcePackSettings(enabled, url, sha1,
                config.getBoolean("resource-pack.force", true),
                text(config, "resource-pack.prompt",
                        "<gray>本服务器使用了界面资源包（社团徽章等），请接受下载。</gray>"),
                url == null ? null : UUID.nameUUIDFromBytes(rawUrl.getBytes(StandardCharsets.UTF_8)));
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

    /** 弹窗图标来源：head = 皮肤头颅（无需资源包）、pack = 资源包自定义物品贴图（高清） */
    public enum IconSource {
        HEAD,
        PACK
    }

    /** dialog 弹窗；{@code iconUrl} 为 null 表示不放徽章图标 */
    public record DialogSettings(
            String title, int width, String button, boolean canCloseWithEscape,
            boolean iconEnabled, URL iconUrl, int iconSize, IconSource iconSource) {
    }

    /** 进服强制下发的资源包；{@code url} 为 null 表示未启用 */
    public record ResourcePackSettings(
            boolean enabled, URL url, byte[] sha1, boolean force, String prompt, UUID packId) {
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
