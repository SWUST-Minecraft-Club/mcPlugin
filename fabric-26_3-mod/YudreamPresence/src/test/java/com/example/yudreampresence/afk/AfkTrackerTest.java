package com.example.yudreampresence.afk;

import com.example.yudreampresence.report.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkTrackerTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final PlayerSnapshot SNAPSHOT = new PlayerSnapshot(PLAYER, "Steve");

    @Test
    void startsAfkOnlyAfterTheTimeout() {
        List<String> events = new CopyOnWriteArrayList<>();
        AfkTracker tracker = new AfkTracker(1000L, listener(events));

        tracker.markOnline(SNAPSHOT, 0L);
        tracker.checkAfk(999L, List.of(SNAPSHOT));
        assertFalse(tracker.isAfk(PLAYER));
        assertTrue(events.isEmpty());

        tracker.checkAfk(1000L, List.of(SNAPSHOT));
        assertTrue(tracker.isAfk(PLAYER));
        assertEquals(List.of("AFK_START:1000"), events);

        tracker.checkAfk(5000L, List.of(SNAPSHOT));
        assertEquals(List.of("AFK_START:1000"), events, "AFK_START 只发一次");
    }

    @Test
    void activityEndsAfkAndRestartsTheTimer() {
        List<String> events = new CopyOnWriteArrayList<>();
        AfkTracker tracker = new AfkTracker(1000L, listener(events));

        tracker.markOnline(SNAPSHOT, 0L);
        tracker.checkAfk(1000L, List.of(SNAPSHOT));
        tracker.markActive(SNAPSHOT, 1500L);

        assertFalse(tracker.isAfk(PLAYER));
        assertEquals(List.of("AFK_START:1000", "AFK_END:1500"), events);

        tracker.checkAfk(2499L, List.of(SNAPSHOT));
        assertFalse(tracker.isAfk(PLAYER));
        tracker.checkAfk(2500L, List.of(SNAPSHOT));
        assertTrue(tracker.isAfk(PLAYER));
        assertEquals(List.of("AFK_START:1000", "AFK_END:1500", "AFK_START:2500"), events);
    }

    @Test
    void quitRemovesStateWithoutSendingAfkEnd() {
        List<String> events = new CopyOnWriteArrayList<>();
        AfkTracker tracker = new AfkTracker(1000L, listener(events));

        tracker.markOnline(SNAPSHOT, 0L);
        tracker.checkAfk(1000L, List.of(SNAPSHOT));
        tracker.markOffline(PLAYER);

        assertFalse(tracker.isAfk(PLAYER));
        assertEquals(List.of("AFK_START:1000"), events, "quit 由 quit 事件收尾，不再补 AFK_END");

        tracker.checkAfk(2000L, List.of());
        assertEquals(List.of("AFK_START:1000"), events);
    }

    @Test
    void staleStateIsRemovedWhenThePlayerIsNotInTheOnlineRoster() {
        AfkTracker tracker = new AfkTracker(1000L, listener(new ArrayList<>()));

        tracker.markOnline(SNAPSHOT, 0L);
        tracker.checkAfk(2000L, List.of());

        assertFalse(tracker.isAfk(PLAYER));
    }

    private static AfkTracker.Listener listener(List<String> events) {
        return new AfkTracker.Listener() {
            @Override
            public void onAfkStart(PlayerSnapshot player, long eventAt) {
                events.add("AFK_START:" + eventAt);
            }

            @Override
            public void onAfkEnd(PlayerSnapshot player, long eventAt) {
                events.add("AFK_END:" + eventAt);
            }
        };
    }
}
