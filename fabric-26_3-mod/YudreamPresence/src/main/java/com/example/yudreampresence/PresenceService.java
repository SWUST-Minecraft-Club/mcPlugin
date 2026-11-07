package com.example.yudreampresence;

import com.example.yudreampresence.afk.AfkTracker;
import com.example.yudreampresence.config.PresenceConfig;
import com.example.yudreampresence.report.PlayerEventType;
import com.example.yudreampresence.report.PlayerSnapshot;
import com.example.yudreampresence.report.ReportSink;

import java.util.Collection;
import java.util.List;

/**
 * 在线状态业务：把加入 / 退出 / 操作 / 周期检查翻译成 YuDream 的四类事件。
 *
 * <p>不引用任何 Minecraft 类型，便于单元测试；服务端适配层只负责把 {@code ServerPlayer}
 * 转成 {@link PlayerSnapshot}。
 */
public final class PresenceService {

    private final PresenceConfig config;
    private final ReportSink sink;
    private final AfkTracker tracker;

    public PresenceService(PresenceConfig config, ReportSink sink) {
        this.config = config;
        this.sink = sink;
        this.tracker = new AfkTracker(config.afkTimeoutMs(), new AfkTracker.Listener() {
            @Override
            public void onAfkStart(PlayerSnapshot player, long eventAt) {
                sink.submit(PlayerEventType.AFK_START, player.playerId(), player.playerName(), eventAt);
            }

            @Override
            public void onAfkEnd(PlayerSnapshot player, long eventAt) {
                sink.submit(PlayerEventType.AFK_END, player.playerId(), player.playerName(), eventAt);
            }
        });
    }

    public void onJoin(PlayerSnapshot player, long eventAt) {
        if (config.afkEnabled()) {
            tracker.markOnline(player, eventAt);
        }
        sink.submit(PlayerEventType.JOIN, player.playerId(), player.playerName(), eventAt);
    }

    public void onQuit(PlayerSnapshot player, long eventAt) {
        if (config.afkEnabled()) {
            tracker.markOffline(player.playerId());
        }
        sink.submit(PlayerEventType.QUIT, player.playerId(), player.playerName(), eventAt);
    }

    public void onActivity(PlayerSnapshot player, long eventAt) {
        if (config.afkEnabled()) {
            tracker.markActive(player, eventAt);
        }
    }

    public void checkAfk(long now, Collection<PlayerSnapshot> onlinePlayers) {
        if (config.afkEnabled()) {
            tracker.checkAfk(now, List.copyOf(onlinePlayers));
        }
    }

    public void snapshot(Collection<PlayerSnapshot> players, long observedAt) {
        if (config.snapshotEnabled()) {
            sink.submitSnapshot(List.copyOf(players), observedAt);
        }
    }

    public AfkTracker tracker() {
        return tracker;
    }
}
