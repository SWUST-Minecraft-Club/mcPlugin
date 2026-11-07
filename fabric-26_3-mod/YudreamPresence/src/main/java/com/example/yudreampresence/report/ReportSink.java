package com.example.yudreampresence.report;

import java.util.List;
import java.util.UUID;

/** 上报入口；核心业务只依赖它，测试里可以换成记录器。 */
public interface ReportSink {

    void submit(PlayerEventType type, UUID playerId, String playerName, long eventAt);

    void submitSnapshot(List<PlayerSnapshot> players, long observedAt);
}
