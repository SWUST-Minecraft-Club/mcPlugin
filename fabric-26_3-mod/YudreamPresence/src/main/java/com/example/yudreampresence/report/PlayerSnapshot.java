package com.example.yudreampresence.report;

import java.util.UUID;

/** 上报所需的最小玩家快照：UUID + 当前名称。 */
public record PlayerSnapshot(UUID playerId, String playerName) {
}
