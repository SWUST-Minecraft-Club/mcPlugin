package com.example.yudreampresence;

import com.example.yudreampresence.config.PresenceConfig;
import com.example.yudreampresence.report.PendingJournal;
import com.example.yudreampresence.report.PlayerEventType;
import com.example.yudreampresence.report.PlayerSnapshot;
import com.example.yudreampresence.report.ReportQueue;
import com.example.yudreampresence.report.YudreamApiClient;
import com.example.yudreampresence.util.Slf4jPresenceLog;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric 服务端入口，也是 Mixin 与服务端对象之间的静态桥。
 *
 * <p>启动时读配置、建队列；第一次拿到 {@link MinecraftServer} 时挂上快照 / AFK 调度，
 * 停服时补发 quit（可选）与空快照并等待队列 flush。
 *
 * <p>所有玩家集合的遍历都在服务端线程（Mixin 注入点或 {@code executeIfPossible} 排队后）执行。
 */
public final class YudreamPresence implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("YudreamPresence");
    private static final PresenceLog LOG = new Slf4jPresenceLog(LOGGER);
    private static final Path CONFIG_FILE = Paths.get("config", "yudream-presence.properties");

    private static final AtomicBoolean ATTACHED = new AtomicBoolean();
    private static final AtomicBoolean STOPPED = new AtomicBoolean();
    private static final Map<UUID, BlockPosition> LAST_BLOCKS = new ConcurrentHashMap<>();

    private static PresenceConfig config;
    private static PresenceService service;
    private static ReportQueue queue;
    private static ScheduledExecutorService scheduler;
    private static volatile MinecraftServer server;

    @Override
    public void onInitializeServer() {
        try {
            config = PresenceConfig.load(CONFIG_FILE);
        } catch (Exception e) {
            LOG.warn("读取配置失败，YudreamPresence 不启用：" + CONFIG_FILE.toAbsolutePath(), e);
            return;
        }
        if (!config.enabled()) {
            LOG.info("配置里 enabled=false，YudreamPresence 不启动");
            return;
        }
        if (!config.configured()) {
            LOG.warn("仍未填全 base-url / server-id / api-key，YudreamPresence 不上报；配置文件："
                    + CONFIG_FILE.toAbsolutePath());
            return;
        }

        YudreamApiClient client = new YudreamApiClient(
                config.baseUrl(), config.serverId(), config.apiKey(),
                config.connectTimeoutMs(), config.readTimeoutMs());
        PendingJournal journal = null;
        if (config.durableQueueEnabled()) {
            Path journalFile = CONFIG_FILE.getParent().resolve("yudream-presence").resolve("pending-reports.tsv");
            try {
                journal = new PendingJournal(journalFile, config.maxPending(), LOG);
            } catch (Exception e) {
                LOG.warn("待发上报日志不可用，本次只走内存队列：" + journalFile.toAbsolutePath(), e);
            }
        }
        queue = new ReportQueue(config, client, journal, LOG);
        service = new PresenceService(config, queue);
        scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "YudreamPresence-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        LOGGER.info("[YudreamPresence] 已启用：{}，待发落盘={}", config.describe(), journal != null ? "on" : "off");
    }

    /** 第一次拿到服务端对象时启动周期任务；重复调用无副作用。 */
    public static void attachServer(MinecraftServer minecraftServer) {
        if (minecraftServer == null || service == null || STOPPED.get()) {
            return;
        }
        server = minecraftServer;
        if (!ATTACHED.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService executor = scheduler;
        if (executor == null) {
            return;
        }
        if (config.syncOnlineOnEnable()) {
            executor.schedule(() -> onServerThread(YudreamPresence::syncOnline), 1L, TimeUnit.SECONDS);
        }
        if (config.snapshotEnabled()) {
            executor.scheduleWithFixedDelay(() -> onServerThread(YudreamPresence::snapshotNow),
                    1L, config.snapshotIntervalSeconds(), TimeUnit.SECONDS);
        }
        if (config.afkEnabled()) {
            executor.scheduleWithFixedDelay(() -> onServerThread(YudreamPresence::checkAfk),
                    config.afkCheckIntervalSeconds(), config.afkCheckIntervalSeconds(), TimeUnit.SECONDS);
        }
        LOGGER.info("[YudreamPresence] 服务端已接管：在线名单 {}s 一次，AFK 检查 {}s 一次",
                config.snapshotIntervalSeconds(), config.afkCheckIntervalSeconds());
    }

    public static void onJoin(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get()) {
            return;
        }
        PlayerSnapshot snapshot = snapshot(player);
        LAST_BLOCKS.put(player.getUUID(), BlockPosition.of(player));
        service.onJoin(snapshot, System.currentTimeMillis());
    }

    public static void onQuit(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get()) {
            return;
        }
        LAST_BLOCKS.remove(player.getUUID());
        service.onQuit(snapshot(player), System.currentTimeMillis());
    }

    public static void onMove(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get() || !config.resetOnMove()) {
            return;
        }
        UUID playerId = player.getUUID();
        BlockPosition current = BlockPosition.of(player);
        BlockPosition previous = LAST_BLOCKS.get(playerId);
        if (current.equals(previous)) {
            return;
        }
        LAST_BLOCKS.put(playerId, current);
        if (previous == null) {
            return;
        }
        service.onActivity(snapshot(player), System.currentTimeMillis());
    }

    public static void onChatActivity(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get() || !config.resetOnChat()) {
            return;
        }
        service.onActivity(snapshot(player), System.currentTimeMillis());
    }

    public static void onCommandActivity(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get() || !config.resetOnCommand()) {
            return;
        }
        service.onActivity(snapshot(player), System.currentTimeMillis());
    }

    public static void onInteractActivity(ServerPlayer player) {
        if (player == null || service == null || STOPPED.get() || !config.resetOnInteract()) {
            return;
        }
        service.onActivity(snapshot(player), System.currentTimeMillis());
    }

    /** 停服：补发可选 quit + 空快照，并等待队列 flush。 */
    public static void onServerStopping(MinecraftServer minecraftServer) {
        if (!STOPPED.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService executor = scheduler;
        if (executor != null) {
            executor.shutdownNow();
        }
        PresenceService currentService = service;
        ReportQueue currentQueue = queue;
        if (currentService == null || currentQueue == null || minecraftServer == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            List<PlayerSnapshot> online = onlineSnapshots(minecraftServer);
            if (config.reportQuitOnDisable()) {
                for (PlayerSnapshot player : online) {
                    currentQueue.submit(PlayerEventType.QUIT, player.playerId(), player.playerName(), now);
                }
            }
            if (config.snapshotEnabled()) {
                currentQueue.submitSnapshot(List.of(), now);
            }
        } catch (Throwable t) {
            LOG.warn("停服上报失败", t);
        } finally {
            currentQueue.shutdown(config.flushTimeoutMs());
        }
    }

    private static void syncOnline() {
        MinecraftServer current = server;
        if (current == null || service == null || STOPPED.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<ServerPlayer> players = new ArrayList<>(current.getPlayerList().getPlayers());
        List<PlayerSnapshot> snapshots = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            PlayerSnapshot snapshot = snapshot(player);
            snapshots.add(snapshot);
            LAST_BLOCKS.put(player.getUUID(), BlockPosition.of(player));
            service.onJoin(snapshot, now);
        }
        service.snapshot(snapshots, now);
    }

    private static void snapshotNow() {
        MinecraftServer current = server;
        if (current == null || service == null || STOPPED.get()) {
            return;
        }
        service.snapshot(onlineSnapshots(current), System.currentTimeMillis());
    }

    private static void checkAfk() {
        MinecraftServer current = server;
        if (current == null || service == null || STOPPED.get()) {
            return;
        }
        service.checkAfk(System.currentTimeMillis(), onlineSnapshots(current));
    }

    private static void onServerThread(Runnable task) {
        MinecraftServer current = server;
        if (current == null || STOPPED.get()) {
            return;
        }
        try {
            current.executeIfPossible(task);
        } catch (Throwable t) {
            LOG.warn("调度服务端任务失败", t);
        }
    }

    private static List<PlayerSnapshot> onlineSnapshots(MinecraftServer current) {
        List<ServerPlayer> players = new ArrayList<>(current.getPlayerList().getPlayers());
        List<PlayerSnapshot> snapshots = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            snapshots.add(snapshot(player));
        }
        return snapshots;
    }

    private static PlayerSnapshot snapshot(ServerPlayer player) {
        return new PlayerSnapshot(player.getUUID(), player.getName().getString());
    }

    private record BlockPosition(int x, int y, int z) {

        private static BlockPosition of(ServerPlayer player) {
            return new BlockPosition(floor(player.getX()), floor(player.getY()), floor(player.getZ()));
        }

        private static int floor(double value) {
            return (int) Math.floor(value);
        }
    }
}
