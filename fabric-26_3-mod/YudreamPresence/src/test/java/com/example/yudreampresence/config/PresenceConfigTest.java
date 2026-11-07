package com.example.yudreampresence.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceConfigTest {

    @Test
    void releasesDefaultFileWithFlatKeys(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("yudream-presence.properties");

        PresenceConfig config = PresenceConfig.load(file);

        assertTrue(Files.isRegularFile(file));
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("http.connect-timeout-ms=5000"));
        assertTrue(content.contains("durable-queue.max-pending=5000"));
        assertFalse(config.configured(), "默认文件没有 server-id / api-key，不应上报");
        assertEquals(5000, config.connectTimeoutMs());
        assertEquals(300, config.afkTimeoutSeconds());
        assertEquals(60, config.snapshotIntervalSeconds());
    }

    @Test
    void stripsTrailingSlashAndReadsCustomValues(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("yudream-presence.properties");
        Files.writeString(file, """
                enabled=true
                base-url=http://127.0.0.1:8080///
                server-id=abc
                api-key=secret
                http.retry-attempts=5
                http.retry-delay-ms=250
                afk.timeout-seconds=120
                afk.enabled=false
                snapshot.interval-seconds=15
                snapshot.enabled=false
                shutdown.report-quit-on-disable=true
                durable-queue.max-pending=42
                """, StandardCharsets.UTF_8);

        PresenceConfig config = PresenceConfig.load(file);

        assertTrue(config.configured());
        assertEquals("http://127.0.0.1:8080", config.baseUrl());
        assertEquals(5, config.retryAttempts());
        assertEquals(250, config.retryDelayMs());
        assertEquals(120, config.afkTimeoutSeconds());
        assertFalse(config.afkEnabled());
        assertEquals(15, config.snapshotIntervalSeconds());
        assertFalse(config.snapshotEnabled());
        assertTrue(config.reportQuitOnDisable());
        assertEquals(42, config.maxPending());
    }

    @Test
    void clampsOutOfRangeValuesAndFallsBackOnGarbage(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("yudream-presence.properties");
        Files.writeString(file, """
                http.connect-timeout-ms=1
                http.retry-attempts=nope
                http.queue-capacity=999999
                afk.timeout-seconds=1
                snapshot.interval-seconds=1
                durable-queue.max-pending=1
                """, StandardCharsets.UTF_8);

        PresenceConfig config = PresenceConfig.load(file);

        assertEquals(500, config.connectTimeoutMs(), "低于下限要抬到 500");
        assertEquals(3, config.retryAttempts(), "非数字回退默认值");
        assertEquals(100_000, config.queueCapacity(), "高于上限要压到 100000");
        assertEquals(30, config.afkTimeoutSeconds());
        assertEquals(5, config.snapshotIntervalSeconds());
        assertEquals(16, config.maxPending());
    }

    @Test
    void enabledFalseBlocksReportingEvenWhenCredentialsArePresent(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("yudream-presence.properties");
        Files.writeString(file, """
                enabled=false
                base-url=http://127.0.0.1:8080
                server-id=abc
                api-key=secret
                """, StandardCharsets.UTF_8);

        PresenceConfig config = PresenceConfig.load(file);

        assertFalse(config.enabled());
        assertFalse(config.configured());
    }

    @Test
    void emptyBaseUrlIsTreatedAsNotConfigured(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("yudream-presence.properties");
        Files.writeString(file, """
                base-url=
                server-id=abc
                api-key=secret
                """, StandardCharsets.UTF_8);

        PresenceConfig config = PresenceConfig.load(file);

        assertTrue(config.baseUrl().isEmpty());
        assertFalse(config.configured(), "删空 base-url 不应回落到本机地址");
    }
}
