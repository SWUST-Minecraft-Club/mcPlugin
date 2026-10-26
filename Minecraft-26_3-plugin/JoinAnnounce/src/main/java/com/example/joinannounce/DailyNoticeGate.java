package com.example.joinannounce;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「每位玩家每天只弹一次」的节流器。
 *
 * <p>只记在内存里：服务器重启后表自然清空，也就是重启即重置。
 * 跨天在线的玩家不会被弹窗打断——表里存的是他上次看到公告的日期，
 * 他退出后在新的一天再次进服时日期对不上，才会再弹一次。
 *
 * <p>进服事件可能落在不同区域线程，用并发容器；退服不清除记录，
 * 保证同一天内多次进服不会重复弹。
 */
final class DailyNoticeGate {

    private final Map<UUID, LocalDate> lastShown = new ConcurrentHashMap<>();

    /**
     * 今天还没给该玩家弹过就记下并返回 {@code true}；今天已弹过返回 {@code false}。
     *
     * @param zoneOffsetHours 「一天」按这个时区偏移划分
     */
    boolean markIfDue(final UUID playerId, final int zoneOffsetHours) {
        final LocalDate today = LocalDate.now(ZoneOffset.ofHours(zoneOffsetHours));
        final LocalDate previous = this.lastShown.put(playerId, today);
        return previous == null || !previous.equals(today);
    }
}
