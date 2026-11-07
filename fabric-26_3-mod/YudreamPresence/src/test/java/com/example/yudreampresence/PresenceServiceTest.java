package com.example.yudreampresence;

import com.example.yudreampresence.report.PlayerEventType;
import com.example.yudreampresence.report.PlayerSnapshot;
import com.example.yudreampresence.report.ReportSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresenceServiceTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final PlayerSnapshot SNAPSHOT = new PlayerSnapshot(PLAYER, "Steve");

    @Test
    void joinQuitAfkAndSnapshotAreWiredToTheSink(@TempDir Path directory) throws Exception {
        RecordingSink sink = new RecordingSink();
        PresenceService service = new PresenceService(TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080",
                "server-id=test",
                "api-key=test",
                "afk.timeout-seconds=30"), sink);

        service.onJoin(SNAPSHOT, 100L);
        service.checkAfk(31_000L, List.of(SNAPSHOT));
        service.onActivity(SNAPSHOT, 32_000L);
        service.snapshot(List.of(SNAPSHOT), 33_000L);
        service.onQuit(SNAPSHOT, 34_000L);

        assertEquals(List.of(
                "event:JOIN:Steve:100",
                "event:AFK_START:Steve:31000",
                "event:AFK_END:Steve:32000",
                "snapshot:1:33000",
                "event:QUIT:Steve:34000"), sink.calls);
    }

    @Test
    void afkDisabledStillReportsJoinAndQuit(@TempDir Path directory) throws Exception {
        RecordingSink sink = new RecordingSink();
        PresenceService service = new PresenceService(TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080",
                "server-id=test",
                "api-key=test",
                "afk.enabled=false"), sink);

        service.onJoin(SNAPSHOT, 100L);
        service.checkAfk(999_999L, List.of(SNAPSHOT));
        service.onQuit(SNAPSHOT, 200L);

        assertEquals(List.of("event:JOIN:Steve:100", "event:QUIT:Steve:200"), sink.calls);
    }

    @Test
    void snapshotDisabledStopsOnlyThePeriodicRoster(@TempDir Path directory) throws Exception {
        RecordingSink sink = new RecordingSink();
        PresenceService service = new PresenceService(TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080",
                "server-id=test",
                "api-key=test",
                "snapshot.enabled=false"), sink);

        service.onJoin(SNAPSHOT, 100L);
        service.snapshot(List.of(SNAPSHOT), 200L);

        assertEquals(List.of("event:JOIN:Steve:100"), sink.calls);
    }

    private static final class RecordingSink implements ReportSink {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void submit(PlayerEventType type, UUID playerId, String playerName, long eventAt) {
            calls.add("event:" + type + ":" + playerName + ":" + eventAt);
        }

        @Override
        public void submitSnapshot(List<PlayerSnapshot> players, long observedAt) {
            calls.add("snapshot:" + players.size() + ":" + observedAt);
        }
    }
}
