package com.example.joinannounce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DailyNoticeGate：每天首次进服才弹、同一天不重复、跨天再弹、玩家之间互不影响。
 */
class DailyNoticeGateTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 26);
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 27);

    @Test
    void firstJoinOfDayIsDue() {
        final DailyNoticeGate gate = new DailyNoticeGate();
        assertTrue(gate.markIfDue(UUID.randomUUID(), DAY1));
    }

    @Test
    void secondJoinSameDayIsNotDue() {
        final DailyNoticeGate gate = new DailyNoticeGate();
        final UUID player = UUID.randomUUID();
        assertTrue(gate.markIfDue(player, DAY1));
        assertFalse(gate.markIfDue(player, DAY1));
    }

    @Test
    void nextDayIsDueAgain() {
        final DailyNoticeGate gate = new DailyNoticeGate();
        final UUID player = UUID.randomUUID();
        assertTrue(gate.markIfDue(player, DAY1));
        assertFalse(gate.markIfDue(player, DAY1));
        assertTrue(gate.markIfDue(player, DAY2));
        assertFalse(gate.markIfDue(player, DAY2));
    }

    @Test
    void playersAreIndependent() {
        final DailyNoticeGate gate = new DailyNoticeGate();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        assertTrue(gate.markIfDue(first, DAY1));
        assertTrue(gate.markIfDue(second, DAY1));
        assertFalse(gate.markIfDue(first, DAY1));
        assertFalse(gate.markIfDue(second, DAY1));
    }
}
