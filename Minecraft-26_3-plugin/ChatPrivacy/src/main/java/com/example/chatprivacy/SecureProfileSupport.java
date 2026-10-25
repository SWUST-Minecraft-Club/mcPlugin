package com.example.chatprivacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 读取 server.properties 的 enforce-secure-profile，只做检查与告警，不修改文件。
 *
 * <p>该选项为 true 时，客户端必须发送签名聊天；装了 No Chat Reports 之类
 * 不发送签名的客户端仍可能无法进入服务器。改为 false 需要重启服务端才生效。
 */
final class SecureProfileSupport {

    private static final AtomicBoolean MISSING_WARNED = new AtomicBoolean();

    private SecureProfileSupport() {
    }

    enum State {
        TRUE,
        FALSE,
        UNKNOWN
    }

    static State read(final JavaPlugin plugin) {
        final File file = findServerProperties(plugin);
        if (file == null) {
            return State.UNKNOWN;
        }

        final Properties properties = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (final IOException ex) {
            plugin.getSLF4JLogger().warn("读取 " + file.getAbsolutePath()
                    + " 失败，跳过 enforce-secure-profile 检查", ex);
            return State.UNKNOWN;
        }

        final String value = properties.getProperty("enforce-secure-profile");
        if (value == null) {
            return State.UNKNOWN;
        }
        return Boolean.parseBoolean(value.trim()) ? State.TRUE : State.FALSE;
    }

    /**
     * 不同启动方式下服务端根目录可能不同，按优先级找 server.properties。
     * 测试服由 run-paper 启动时，工作目录和世界容器都指向 ./run。
     */
    private static File findServerProperties(final JavaPlugin plugin) {
        final File worldContainer = plugin.getServer().getWorldContainer();
        final File dataRoot = serverRootFromDataFolder(plugin);
        final File[] candidates = {
                worldContainer == null ? null : new File(worldContainer, "server.properties"),
                new File("server.properties"),
                dataRoot == null ? null : new File(dataRoot, "server.properties")
        };

        for (final File candidate : candidates) {
            if (candidate != null && candidate.isFile()) {
                return candidate;
            }
        }
        if (MISSING_WARNED.compareAndSet(false, true)) {
            final StringBuilder tried = new StringBuilder();
            for (final File candidate : candidates) {
                if (candidate != null) {
                    if (tried.length() > 0) {
                        tried.append("、");
                    }
                    tried.append(candidate.getAbsolutePath());
                }
            }
            plugin.getSLF4JLogger().warn("找不到 server.properties，已尝试：{}", tried);
        }
        return null;
    }

    private static File serverRootFromDataFolder(final JavaPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) {
            return null;
        }
        // getDataFolder() 在 run-paper 下可能是相对路径（plugins/ChatPrivacy），
        // 直接 getParentFile() 链到 plugins 后第二级会变成 null，必须先转绝对路径。
        final File pluginsDir = dataFolder.getAbsoluteFile().getParentFile();
        return pluginsDir == null ? null : pluginsDir.getParentFile();
    }

    static void logStatus(final JavaPlugin plugin, final ChatPrivacySettings settings) {
        if (!settings.warnOnSecureProfile()) {
            return;
        }
        switch (read(plugin)) {
            case TRUE -> plugin.getComponentLogger().warn(Component.text(
                    "server.properties 的 enforce-secure-profile=true：未签名客户端（例如装了 No Chat Reports 的客户端）"
                            + "可能无法进入服务器；建议改为 false 后重启。本插件不会自动修改该文件。",
                    NamedTextColor.YELLOW));
            case FALSE -> plugin.getComponentLogger().info(Component.text(
                    "server.properties 的 enforce-secure-profile=false：允许未签名客户端进入。",
                    NamedTextColor.GREEN));
            case UNKNOWN -> plugin.getComponentLogger().warn(Component.text(
                    "无法读取 server.properties 的 enforce-secure-profile，跳过检查。",
                    NamedTextColor.GRAY));
        }
    }
}
