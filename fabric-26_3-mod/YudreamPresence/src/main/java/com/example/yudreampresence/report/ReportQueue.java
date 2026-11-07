package com.example.yudreampresence.report;

import com.example.yudreampresence.PresenceLog;
import com.example.yudreampresence.config.PresenceConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步上报队列。
 *
 * <p>进服 / 退服 / AFK 起止是「会改变在线时长」的事件，开了落盘时先进 {@link PendingJournal}，
 * 由单线程 worker 按原始时间戳补发；快照只进内存队列，失败就等下一轮。与现有 Bukkit 插件的
 * {@code ReportQueue} 保持同一套语义。
 */
public final class ReportQueue implements ReportSink {

    private static final long REPLAY_BACKOFF_MS = 30_000L;

    private final PresenceConfig config;
    private final ReportTransport transport;
    private final PendingJournal journal;
    private final PresenceLog log;
    private final BlockingQueue<Task> queue;
    private final ExecutorService executor;

    private volatile boolean running = true;
    private long nextReplayAttemptAt;

    public ReportQueue(PresenceConfig config, ReportTransport transport, PendingJournal journal, PresenceLog log) {
        this.config = config;
        this.transport = transport;
        this.journal = journal;
        this.log = log;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity());
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YudreamPresence-Reporter");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.execute(this::workerLoop);
    }

    @Override
    public void submit(PlayerEventType type, UUID playerId, String playerName, long eventAt) {
        if (!canSubmit()) {
            return;
        }
        if (journal != null) {
            journal.append(type, playerId, playerName, eventAt);
            if (config.logQueued()) {
                log.info("已记录待上报：" + describe(type, playerName, eventAt) + "，pending=" + journal.size());
            }
            return;
        }
        enqueue(Task.event(type, playerId, playerName, eventAt));
    }

    @Override
    public void submitSnapshot(List<PlayerSnapshot> players, long observedAt) {
        if (!canSubmit()) {
            return;
        }
        enqueue(Task.snapshot(players, observedAt));
    }

    public int size() {
        return queue.size() + (journal == null ? 0 : journal.size());
    }

    public void shutdown(long timeoutMs) {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Math.max(timeoutMs, 1L), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private boolean canSubmit() {
        return config.configured();
    }

    private void enqueue(Task task) {
        if (queue.offer(task)) {
            if (config.logQueued()) {
                log.info("已入队：" + describe(task) + "，queue=" + queue.size());
            }
        } else if (config.logFailures()) {
            log.warn("上报队列已满，丢弃：" + describe(task));
        }
    }

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                if (running && journal != null && canSubmit()
                        && System.currentTimeMillis() >= nextReplayAttemptAt) {
                    PendingJournal.Pending pending = journal.peek();
                    if (pending != null) {
                        replay(pending);
                        continue;
                    }
                }
                Task task = queue.poll(500L, TimeUnit.MILLISECONDS);
                if (task != null) {
                    sendWithRetry(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                log.warn("上报线程出现未预期异常", t);
            }
        }
    }

    private void replay(PendingJournal.Pending pending) {
        long startedAt = System.currentTimeMillis();
        try {
            HttpResult result = transport.post(pending.type().remotePath(),
                    JsonPayloads.event(pending.playerId(), pending.playerName(), pending.eventAt()));
            if (result.isSuccess()) {
                journal.remove(pending.id());
                nextReplayAttemptAt = 0L;
                if (config.logSuccess()) {
                    log.info("待发上报已补发：" + describe(pending.type(), pending.playerName(), pending.eventAt())
                            + "，http=" + result.statusCode() + "，pendingLeft=" + journal.size()
                            + "，耗时=" + (System.currentTimeMillis() - startedAt) + "ms");
                }
                return;
            }
            backOffReplay("http=" + result.statusCode());
        } catch (Exception e) {
            backOffReplay("error=" + e.getMessage());
        }
    }

    private void backOffReplay(String detail) {
        nextReplayAttemptAt = System.currentTimeMillis() + REPLAY_BACKOFF_MS;
        if (config.logFailures()) {
            log.warn("待发上报补发失败，30 秒后重试：" + detail + "，pending=" + journal.size());
        }
    }

    private void sendWithRetry(Task task) {
        int attempts = config.retryAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.currentTimeMillis();
            try {
                if (config.logAttempts()) {
                    log.info("发送上报：" + describe(task) + "，attempt=" + attempt + "/" + attempts);
                }
                HttpResult result = send(task);
                long elapsedMs = System.currentTimeMillis() - startedAt;
                if (result.isSuccess()) {
                    if (config.logSuccess()) {
                        log.info("上报成功：" + describe(task) + "，http=" + result.statusCode()
                                + "，attempt=" + attempt + "/" + attempts + "，耗时=" + elapsedMs + "ms");
                    }
                    return;
                }
                if (attempt == attempts) {
                    if (config.logFailures()) {
                        log.warn("上报失败：" + describe(task) + "，http=" + result.statusCode()
                                + "，attempts=" + attempts + "，耗时=" + elapsedMs + "ms，响应=" + trim(result.body()));
                    }
                } else if (config.logAttempts()) {
                    log.warn("上报尝试失败：" + describe(task) + "，http=" + result.statusCode()
                            + "，attempt=" + attempt + "/" + attempts + "，响应=" + trim(result.body()));
                }
            } catch (Exception e) {
                if (attempt == attempts) {
                    if (config.logFailures()) {
                        log.warn("上报失败：" + describe(task) + "，attempts=" + attempts, e);
                    }
                } else if (config.logAttempts()) {
                    log.warn("上报尝试异常：" + describe(task) + "，attempt=" + attempt + "/" + attempts
                            + "，error=" + e.getMessage());
                }
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(config.retryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private HttpResult send(Task task) throws Exception {
        if (task.snapshot) {
            return transport.post("players/snapshot", JsonPayloads.snapshot(task.players, task.observedAt));
        }
        return transport.post(task.type.remotePath(),
                JsonPayloads.event(task.playerId, task.playerName, task.eventAt));
    }

    private String describe(Task task) {
        if (task.snapshot) {
            return "type=SNAPSHOT, players=" + task.players.size()
                    + (config.logPayload() ? ", observedAt=" + task.observedAt : "");
        }
        return describe(task.type, task.playerName, task.eventAt);
    }

    private String describe(PlayerEventType type, String playerName, long eventAt) {
        StringBuilder message = new StringBuilder("type=").append(type);
        message.append(", endpoint=/").append(type.remotePath());
        message.append(", player=").append(playerName);
        if (config.logPayload()) {
            message.append(", eventAt=").append(eventAt);
        }
        return message.toString();
    }

    private static String trim(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

    private static final class Task {
        private final boolean snapshot;
        private final PlayerEventType type;
        private final UUID playerId;
        private final String playerName;
        private final long eventAt;
        private final List<PlayerSnapshot> players;
        private final long observedAt;

        private Task(boolean snapshot, PlayerEventType type, UUID playerId, String playerName,
                     long eventAt, List<PlayerSnapshot> players, long observedAt) {
            this.snapshot = snapshot;
            this.type = type;
            this.playerId = playerId;
            this.playerName = playerName;
            this.eventAt = eventAt;
            this.players = players == null ? List.of() : List.copyOf(players);
            this.observedAt = observedAt;
        }

        private static Task event(PlayerEventType type, UUID playerId, String playerName, long eventAt) {
            return new Task(false, type, playerId, playerName, eventAt, null, eventAt);
        }

        private static Task snapshot(Collection<PlayerSnapshot> players, long observedAt) {
            return new Task(true, null, null, null, observedAt, new ArrayList<>(players), observedAt);
        }
    }
}
