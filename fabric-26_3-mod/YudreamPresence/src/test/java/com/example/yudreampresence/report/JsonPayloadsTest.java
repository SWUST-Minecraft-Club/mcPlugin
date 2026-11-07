package com.example.yudreampresence.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPayloadsTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void eventBodyMatchesTheBukkitBridge() {
        String body = JsonPayloads.event(PLAYER, "Steve", 1_700_000_000_000L);

        assertEquals("{\"playerId\":\"11111111-2222-3333-4444-555555555555\","
                + "\"playerName\":\"Steve\",\"eventAt\":1700000000000}", body);
    }

    @Test
    void snapshotKeepsPlayerOrderAndObservedAt() {
        String body = JsonPayloads.snapshot(List.of(
                new PlayerSnapshot(PLAYER, "Steve"),
                new PlayerSnapshot(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "Alex")
        ), 1_700_000_000_000L);

        assertEquals("{\"observedAt\":1700000000000,\"players\":["
                + "{\"playerId\":\"11111111-2222-3333-4444-555555555555\",\"playerName\":\"Steve\"},"
                + "{\"playerId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"playerName\":\"Alex\"}]}", body);
    }

    @Test
    void snapshotOfNobodyIsStillAnAuthoritativeEmptyRoster() {
        assertEquals("{\"observedAt\":42,\"players\":[]}", JsonPayloads.snapshot(List.of(), 42L));
    }

    @Test
    void escapeHandlesQuotesBackslashesAndControlCharacters() {
        assertEquals("a\\\"b\\\\c\\n\\t\\u0001", JsonPayloads.escape("a\"b\\c\n\t\u0001"));
        assertEquals("", JsonPayloads.escape(null));
    }

    @Test
    void snapshotIsNotAffectedByLaterMutationOfTheSourceList() {
        java.util.ArrayList<PlayerSnapshot> players = new java.util.ArrayList<>();
        players.add(new PlayerSnapshot(PLAYER, "Steve"));
        String before = JsonPayloads.snapshot(players, 1L);
        players.add(new PlayerSnapshot(PLAYER, "Alex"));

        assertTrue(before.contains("Steve"));
        assertTrue(!before.contains("Alex"));
    }
}
