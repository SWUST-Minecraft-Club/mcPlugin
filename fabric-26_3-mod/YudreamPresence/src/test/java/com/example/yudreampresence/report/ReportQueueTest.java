package com.example.yudreampresence.report;

import com.example.yudreampresence.TestConfigs;
import com.example.yudreampresence.TestLog;
import com.example.yudreampresence.config.PresenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportQueueTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final PlayerSnapshot SNAPSHOT = new PlayerSnapshot(PLAYER, "Steve");

    @Test
    void sendsEventAndSnapshotWithTheExpectedPaths(@TempDir Path directory) throws Exception {
        ReportingTransport transport = new ReportingTransport(0);
        PresenceConfig config = TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080", "server-id=test", "api-key=test",
                "durable-queue.enabled=false", "http.retry-attempts=1", "log.success=false");
        ReportQueue queue = new ReportQueue(config, transport, null, new TestLog());
        try {
            queue.submit(PlayerEventType.JOIN, PLAYER, "Steve", 100L);
            awaitSize(transport.paths, 1);

            assertEquals("players/join", transport.paths.get(0));
            assertTrue(transport.bodies.get(0).contains("\"playerId\":\"" + PLAYER + "\""));
            assertTrue(transport.bodies.get(0).contains("\"eventAt\":100"));

            queue.submitSnapshot(List.of(SNAPSHOT), 200L);
            awaitSize(transport.paths, 2);

            assertEquals("players/snapshot", transport.paths.get(1));
            assertTrue(transport.bodies.get(1).contains("\"observedAt\":200"));
            assertTrue(transport.bodies.get(1).contains("\"playerName\":\"Steve\""));
        } finally {
            queue.shutdown(1000L);
        }
    }

    @Test
    void retriesTransientFailures(@TempDir Path directory) throws Exception {
        ReportingTransport transport = new ReportingTransport(1);
        PresenceConfig config = TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080", "server-id=test", "api-key=test",
                "durable-queue.enabled=false", "http.retry-attempts=2", "http.retry-delay-ms=10",
                "log.success=false");
        ReportQueue queue = new ReportQueue(config, transport, null, new TestLog());
        try {
            queue.submit(PlayerEventType.QUIT, PLAYER, "Steve", 300L);
            awaitSize(transport.paths, 2);

            assertEquals(2, transport.calls.get());
            assertEquals("players/quit", transport.paths.get(0));
            assertEquals("players/quit", transport.paths.get(1));
        } finally {
            queue.shutdown(1000L);
        }
    }

    @Test
    void replaysPendingJournalBeforeTheMemoryQueue(@TempDir Path directory) throws Exception {
        ReportingTransport transport = new ReportingTransport(0);
        PresenceConfig config = TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080", "server-id=test", "api-key=test",
                "http.retry-attempts=1", "log.success=false");
        TestLog log = new TestLog();
        PendingJournal journal = new PendingJournal(directory.resolve("pending.tsv"), 10, log);
        journal.append(PlayerEventType.AFK_START, PLAYER, "Steve", 400L);

        ReportQueue queue = new ReportQueue(config, transport, journal, log);
        try {
            awaitSize(transport.paths, 1);

            assertEquals("players/afk/start", transport.paths.get(0));
            assertTrue(transport.bodies.get(0).contains("\"eventAt\":400"));
            assertEquals(0, journal.size(), "补发成功后应从落盘日志删除");
        } finally {
            queue.shutdown(1000L);
        }
    }

    @Test
    void durableSubmitGoesThroughTheJournal(@TempDir Path directory) throws Exception {
        ReportingTransport transport = new ReportingTransport(0);
        PresenceConfig config = TestConfigs.write(directory,
                "base-url=http://127.0.0.1:8080", "server-id=test", "api-key=test",
                "http.retry-attempts=1", "log.success=false");
        TestLog log = new TestLog();
        PendingJournal journal = new PendingJournal(directory.resolve("pending.tsv"), 10, log);
        ReportQueue queue = new ReportQueue(config, transport, journal, log);
        try {
            queue.submit(PlayerEventType.JOIN, PLAYER, "Steve", 500L);
            awaitSize(transport.paths, 1);

            assertEquals("players/join", transport.paths.get(0));
            assertEquals(0, journal.size());
        } finally {
            queue.shutdown(1000L);
        }
    }

    private static void awaitSize(List<?> list, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (list.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(expected, list.size());
    }

    private static final class ReportingTransport implements ReportTransport {

        private final AtomicInteger calls = new AtomicInteger();
        private final int failuresBeforeSuccess;
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final List<String> bodies = new CopyOnWriteArrayList<>();

        private ReportingTransport(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public HttpResult post(String relativePath, String jsonBody) throws IOException {
            int call = calls.incrementAndGet();
            paths.add(relativePath);
            bodies.add(jsonBody);
            if (call <= failuresBeforeSuccess) {
                return new HttpResult(500, "transient");
            }
            return new HttpResult(200, "{}");
        }
    }
}
