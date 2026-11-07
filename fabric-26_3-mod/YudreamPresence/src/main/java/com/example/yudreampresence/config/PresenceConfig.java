package com.example.yudreampresence.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * {@code config/yudream-presence.properties} 的读取与默认值。
 *
 * <p>键名与现有的 Bukkit 上报插件保持一致，方便同一个 YuDream Admin 后台、同一套运维口径。
 * 新增键一律有兜底值；文件不存在时释放带注释的默认文件。
 */
public final class PresenceConfig {

    private static final String DEFAULT_FILE = """
            # YuDream 在线情况上报（Fabric 26.3 服务端）
            #
            # 只上报在线状态：进服 / 退服 / AFK 起止 / 定时全量名单。
            # 不上报聊天、死亡、成就；也不走代理 bridge（直连 YuDream Admin）。
            #
            # 未填 server-id 或 api-key 时模组不启动上报，服务端照常运行。

            enabled=true

            # YuDream Admin 地址，不要以 / 结尾。
            base-url=http://127.0.0.1:8080

            # 在 YuDream Admin 的「MC 服务器」里创建的服务器 id。
            server-id=

            # 需要 plugin:minecraft-server:report 权限的 API key。
            api-key=

            # HTTP
            http.connect-timeout-ms=5000
            http.read-timeout-ms=8000
            http.retry-attempts=3
            http.retry-delay-ms=1500
            http.queue-capacity=1000

            # 日志
            # 记录每条进入队列的上报。
            log.queued=false
            # 记录每次 HTTP 尝试（含重试）。
            log.attempts=false
            # 记录成功的上报。
            log.success=true
            # 记录最终失败与待发补发失败。
            log.failures=true
            # 在日志里附带 playerId / eventAt（不记录 API key）。
            log.payload=false

            # AFK
            afk.enabled=true
            afk.timeout-seconds=300
            afk.check-interval-seconds=30
            afk.reset-on-move=true
            afk.reset-on-chat=true
            afk.reset-on-command=true
            afk.reset-on-interact=true

            # 定时全量在线名单
            snapshot.enabled=true
            snapshot.interval-seconds=60

            # 服务端启动后把当时已经在线的玩家再上报一次（含全量快照）。
            startup.sync-online-on-enable=true

            # 停服时是否逐个补发 quit；默认只上报空快照，避免插件式 reload 时误报。
            shutdown.report-quit-on-disable=false
            shutdown.flush-timeout-ms=5000

            # 进服 / 退服 / AFK 起止先落盘，后端恢复后按原始时间戳补发。
            durable-queue.enabled=true
            durable-queue.max-pending=5000
            """;

    private final boolean enabled;
    private final String baseUrl;
    private final String serverId;
    private final String apiKey;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int retryAttempts;
    private final int retryDelayMs;
    private final int queueCapacity;

    private final boolean logQueued;
    private final boolean logAttempts;
    private final boolean logSuccess;
    private final boolean logFailures;
    private final boolean logPayload;

    private final boolean afkEnabled;
    private final int afkTimeoutSeconds;
    private final int afkCheckIntervalSeconds;
    private final boolean resetOnMove;
    private final boolean resetOnChat;
    private final boolean resetOnCommand;
    private final boolean resetOnInteract;

    private final boolean snapshotEnabled;
    private final int snapshotIntervalSeconds;

    private final boolean syncOnlineOnEnable;
    private final boolean reportQuitOnDisable;
    private final int flushTimeoutMs;

    private final boolean durableQueueEnabled;
    private final int maxPending;

    private PresenceConfig(Properties properties) {
        this.enabled = bool(properties, "enabled", true);
        // 默认文件里给了 http://127.0.0.1:8080；键被删空时按“未配置”处理，避免误连本机。
        this.baseUrl = stripTrailingSlash(text(properties, "base-url", ""));
        this.serverId = text(properties, "server-id", "").trim();
        this.apiKey = text(properties, "api-key", "").trim();

        this.connectTimeoutMs = integer(properties, "http.connect-timeout-ms", 5000, 500, 120_000);
        this.readTimeoutMs = integer(properties, "http.read-timeout-ms", 8000, 500, 120_000);
        this.retryAttempts = integer(properties, "http.retry-attempts", 3, 1, 10);
        this.retryDelayMs = integer(properties, "http.retry-delay-ms", 1500, 10, 60_000);
        this.queueCapacity = integer(properties, "http.queue-capacity", 1000, 16, 100_000);

        this.logQueued = bool(properties, "log.queued", false);
        this.logAttempts = bool(properties, "log.attempts", false);
        this.logSuccess = bool(properties, "log.success", true);
        this.logFailures = bool(properties, "log.failures", true);
        this.logPayload = bool(properties, "log.payload", false);

        this.afkEnabled = bool(properties, "afk.enabled", true);
        this.afkTimeoutSeconds = integer(properties, "afk.timeout-seconds", 300, 30, 86_400);
        this.afkCheckIntervalSeconds = integer(properties, "afk.check-interval-seconds", 30, 5, 3600);
        this.resetOnMove = bool(properties, "afk.reset-on-move", true);
        this.resetOnChat = bool(properties, "afk.reset-on-chat", true);
        this.resetOnCommand = bool(properties, "afk.reset-on-command", true);
        this.resetOnInteract = bool(properties, "afk.reset-on-interact", true);

        this.snapshotEnabled = bool(properties, "snapshot.enabled", true);
        this.snapshotIntervalSeconds = integer(properties, "snapshot.interval-seconds", 60, 5, 3600);

        this.syncOnlineOnEnable = bool(properties, "startup.sync-online-on-enable", true);
        this.reportQuitOnDisable = bool(properties, "shutdown.report-quit-on-disable", false);
        this.flushTimeoutMs = integer(properties, "shutdown.flush-timeout-ms", 5000, 500, 60_000);

        this.durableQueueEnabled = bool(properties, "durable-queue.enabled", true);
        this.maxPending = integer(properties, "durable-queue.max-pending", 5000, 16, 100_000);
    }

    /** 读取配置；文件不存在时先释放默认文件。 */
    public static PresenceConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new PresenceConfig(properties);
    }

    /** 三项凭据齐全才算可上报；否则只告警、不发请求。 */
    public boolean configured() {
        return enabled && !baseUrl.isBlank() && !serverId.isBlank() && !apiKey.isBlank();
    }

    /** 启动日志用的安全摘要（不含 API key）。 */
    public String describe() {
        return "base-url=" + baseUrl + ", server-id=" + serverId
                + ", snapshot=" + snapshotIntervalSeconds + "s"
                + ", afk=" + (afkEnabled ? afkTimeoutSeconds + "s" : "off")
                + ", durable-queue=" + (durableQueueEnabled ? "on" : "off");
    }

    private static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String serverId() {
        return serverId;
    }

    public String apiKey() {
        return apiKey;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public int retryAttempts() {
        return retryAttempts;
    }

    public int retryDelayMs() {
        return retryDelayMs;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public boolean logQueued() {
        return logQueued;
    }

    public boolean logAttempts() {
        return logAttempts;
    }

    public boolean logSuccess() {
        return logSuccess;
    }

    public boolean logFailures() {
        return logFailures;
    }

    public boolean logPayload() {
        return logPayload;
    }

    public boolean afkEnabled() {
        return afkEnabled;
    }

    public int afkTimeoutSeconds() {
        return afkTimeoutSeconds;
    }

    public long afkTimeoutMs() {
        return afkTimeoutSeconds * 1000L;
    }

    public int afkCheckIntervalSeconds() {
        return afkCheckIntervalSeconds;
    }

    public boolean resetOnMove() {
        return resetOnMove;
    }

    public boolean resetOnChat() {
        return resetOnChat;
    }

    public boolean resetOnCommand() {
        return resetOnCommand;
    }

    public boolean resetOnInteract() {
        return resetOnInteract;
    }

    public boolean snapshotEnabled() {
        return snapshotEnabled;
    }

    public int snapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public boolean syncOnlineOnEnable() {
        return syncOnlineOnEnable;
    }

    public boolean reportQuitOnDisable() {
        return reportQuitOnDisable;
    }

    public int flushTimeoutMs() {
        return flushTimeoutMs;
    }

    public boolean durableQueueEnabled() {
        return durableQueueEnabled;
    }

    public int maxPending() {
        return maxPending;
    }
}
