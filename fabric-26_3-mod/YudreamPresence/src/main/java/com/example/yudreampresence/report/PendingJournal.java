package com.example.yudreampresence.report;

import com.example.yudreampresence.PresenceLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 待发上报落盘队列（一行一条 TSV）。
 *
 * <p>只记录会影响在线时长的事件（进服 / 退服 / AFK 起止）：后端不可用、未配置或网络失败时
 * 先写进这里，后端恢复后按原始时间戳按序补发；进程重启也不丢。快照不落盘，下一轮自愈。
 *
 * <p>玩家名只可能来自 Minecraft 用户名或离线服生成的名称，不会包含制表符/换行；UUID 固定格式，
 * 所以直接用 TSV 而不引入 JSON 解析依赖。
 */
public final class PendingJournal {

    public record Pending(long id, PlayerEventType type, UUID playerId, String playerName, long eventAt) {
    }

    private final Path file;
    private final int maxPending;
    private final PresenceLog log;
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private long nextId = 1L;

    public PendingJournal(Path file, int maxPending, PresenceLog log) {
        this.file = file;
        this.maxPending = Math.max(1, maxPending);
        this.log = log;
        load();
    }

    public Path file() {
        return file;
    }

    public synchronized int size() {
        return pending.size();
    }

    /** 追加一条待发上报，返回编号；后端确认成功后用 {@link #remove(long)} 删除。 */
    public synchronized long append(PlayerEventType type, UUID playerId, String playerName, long eventAt) {
        long id = nextId++;
        pending.put(id, new Pending(id, type, playerId, playerName, eventAt));
        trim();
        flush();
        return id;
    }

    /** 最老的一条待发上报；没有则为 {@code null}。 */
    public synchronized Pending peek() {
        for (Pending entry : pending.values()) {
            return entry;
        }
        return null;
    }

    public synchronized void remove(long id) {
        if (pending.remove(id) != null) {
            flush();
        }
    }

    private void trim() {
        while (pending.size() > maxPending) {
            Long oldest = null;
            for (Long key : pending.keySet()) {
                oldest = key;
                break;
            }
            if (oldest == null) {
                return;
            }
            Pending dropped = pending.remove(oldest);
            if (dropped != null) {
                log.warn("待发上报已满（" + maxPending + "），丢弃最老的 "
                        + dropped.type() + " 事件：" + dropped.playerName() + " (" + dropped.playerId() + ")");
            }
        }
    }

    private void flush() {
        Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                log.warn("无法创建待发上报目录：" + parent, e);
                return;
            }
        }
        StringBuilder content = new StringBuilder();
        for (Pending entry : pending.values()) {
            content.append(entry.id()).append('\t')
                    .append(entry.type().name()).append('\t')
                    .append(entry.eventAt()).append('\t')
                    .append(entry.playerId()).append('\t')
                    .append(entry.playerName()).append('\n');
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, content.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("写入待发上报日志失败：" + file, e);
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        int skipped = 0;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Pending entry = parse(trimmed);
                if (entry == null) {
                    skipped++;
                    continue;
                }
                pending.put(entry.id(), entry);
                if (entry.id() >= nextId) {
                    nextId = entry.id() + 1;
                }
            }
        } catch (IOException e) {
            log.warn("读取待发上报日志失败：" + file, e);
            return;
        }
        if (!pending.isEmpty() || skipped > 0) {
            log.info("已从 " + file.getFileName() + " 恢复 " + pending.size() + " 条待发上报"
                    + (skipped > 0 ? "（忽略 " + skipped + " 行无效数据）" : "")
                    + "，后端恢复后会自动补发。");
        }
    }

    private static Pending parse(String line) {
        String[] fields = line.split("\t", 5);
        if (fields.length != 5) {
            return null;
        }
        try {
            long id = Long.parseLong(fields[0]);
            PlayerEventType type = PlayerEventType.valueOf(fields[1]);
            long eventAt = Long.parseLong(fields[2]);
            UUID playerId = UUID.fromString(fields[3]);
            String playerName = fields[4];
            if (id < 1 || playerName.isBlank()) {
                return null;
            }
            return new Pending(id, type, playerId, playerName, eventAt);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
