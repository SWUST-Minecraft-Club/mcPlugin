package com.example.yudreampresence.afk;

import com.example.yudreampresence.report.PlayerSnapshot;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK 状态机：口径与 Bukkit 插件一致 —— 连续 {@code timeoutMs} 没有任何操作才算 AFK，
 * 操作恢复时补发 AFK_END；退服只移除状态，不额外发 AFK_END（quit 会结束该段会话）。
 *
 * <p>所有调用都在服务端线程；用并发容器只是为了调度器/停服线程能安全读取。
 */
public final class AfkTracker {

    public interface Listener {

        void onAfkStart(PlayerSnapshot player, long eventAt);

        void onAfkEnd(PlayerSnapshot player, long eventAt);
    }

    private record State(long lastActiveAt, boolean afk, String playerName) {
    }

    private final long timeoutMs;
    private final Listener listener;
    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public AfkTracker(long timeoutMs, Listener listener) {
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.listener = listener;
    }

    public void markOnline(PlayerSnapshot player, long now) {
        states.put(player.playerId(), new State(now, false, player.playerName()));
    }

    public void markOffline(UUID playerId) {
        states.remove(playerId);
    }

    /** 记录一次操作；如果之前是 AFK，先补发 AFK_END。 */
    public void markActive(PlayerSnapshot player, long now) {
        boolean[] wasAfk = {false};
        states.compute(player.playerId(), (id, state) -> {
            if (state != null && state.afk) {
                wasAfk[0] = true;
            }
            return new State(now, false, player.playerName());
        });
        if (wasAfk[0]) {
            listener.onAfkEnd(player, now);
        }
    }

    /** 周期检查：对在线玩家中超过阈值且尚未标记的补发 AFK_START。 */
    public void checkAfk(long now, Collection<PlayerSnapshot> onlinePlayers) {
        Set<UUID> online = new HashSet<>();
        for (PlayerSnapshot player : onlinePlayers) {
            online.add(player.playerId());
            State state = states.get(player.playerId());
            if (state == null) {
                states.put(player.playerId(), new State(now, false, player.playerName()));
            } else if (!state.afk && now - state.lastActiveAt >= timeoutMs) {
                State next = new State(state.lastActiveAt, true, player.playerName());
                if (states.replace(player.playerId(), state, next)) {
                    listener.onAfkStart(player, now);
                }
            }
        }
        // 丢掉已经不在线的残留状态（正常退服会 remove，这里兜底）
        states.keySet().removeIf(id -> !online.contains(id));
    }

    public boolean isAfk(UUID playerId) {
        State state = states.get(playerId);
        return state != null && state.afk;
    }
}
