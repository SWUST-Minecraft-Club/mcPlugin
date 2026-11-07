package com.example.yudreampresence.report;

import com.example.yudreampresence.TestLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendingJournalTest {

    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THIRD = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void appendsReloadsAndKeepsEventOrder(@TempDir Path directory) {
        Path file = directory.resolve("pending.tsv");
        TestLog log = new TestLog();
        PendingJournal journal = new PendingJournal(file, 100, log);

        long first = journal.append(PlayerEventType.JOIN, FIRST, "Steve", 100L);
        long second = journal.append(PlayerEventType.AFK_START, SECOND, "Alex", 200L);
        long third = journal.append(PlayerEventType.QUIT, THIRD, "Herobrine", 300L);

        assertEquals(3, journal.size());
        assertEquals(first, journal.peek().id());
        assertEquals(PlayerEventType.JOIN, journal.peek().type());

        PendingJournal reloaded = new PendingJournal(file, 100, new TestLog());
        assertEquals(3, reloaded.size());
        assertEquals(first, reloaded.peek().id());
        assertEquals(PlayerEventType.JOIN, reloaded.peek().type());
        assertEquals("Steve", reloaded.peek().playerName());
        assertEquals(100L, reloaded.peek().eventAt());

        reloaded.remove(second);
        assertEquals(2, reloaded.size());
        assertEquals(first, reloaded.peek().id(), "删掉中间一条后，队首仍是最老的 first");

        PendingJournal again = new PendingJournal(file, 100, new TestLog());
        assertEquals(2, again.size());
        assertEquals(first, again.peek().id(), "删除的中间条目不应在重载后复活");
    }

    @Test
    void trimsOldestWhenOverCapacity(@TempDir Path directory) {
        Path file = directory.resolve("pending.tsv");
        TestLog log = new TestLog();
        PendingJournal journal = new PendingJournal(file, 2, log);

        journal.append(PlayerEventType.JOIN, FIRST, "Steve", 100L);
        long second = journal.append(PlayerEventType.JOIN, SECOND, "Alex", 200L);
        journal.append(PlayerEventType.QUIT, THIRD, "Herobrine", 300L);

        assertEquals(2, journal.size());
        assertEquals(second, journal.peek().id(), "超出上限时丢最老的，队首变成第二条");
        assertEquals(PlayerEventType.JOIN, journal.peek().type());
        assertEquals(1, log.warnings.size());
    }

    @Test
    void skipsCorruptLinesInsteadOfLosingTheWholeFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("pending.tsv");
        Files.writeString(file, """
                1\tJOIN\t100\t11111111-1111-1111-1111-111111111111\tSteve
                broken-line
                2\tNOT_A_TYPE\t200\t22222222-2222-2222-2222-222222222222\tAlex
                3\tAFK_END\t300\t33333333-3333-3333-3333-333333333333\tHerobrine
                """, StandardCharsets.UTF_8);

        TestLog log = new TestLog();
        PendingJournal journal = new PendingJournal(file, 100, log);

        assertEquals(2, journal.size());
        assertNotNull(journal.peek());
        assertEquals(PlayerEventType.JOIN, journal.peek().type());
        assertEquals("Steve", journal.peek().playerName());
        assertEquals(1, log.infos.size());
    }

    @Test
    void emptyJournalPeeksNull(@TempDir Path directory) {
        PendingJournal journal = new PendingJournal(directory.resolve("pending.tsv"), 10, new TestLog());

        assertNull(journal.peek());
        assertEquals(0, journal.size());
    }
}
