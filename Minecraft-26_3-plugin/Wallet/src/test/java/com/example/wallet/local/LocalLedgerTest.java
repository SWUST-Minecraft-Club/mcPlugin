package com.example.wallet.local;

import static org.junit.jupiter.api.Assertions.*;
import com.example.wallet.api.WalletException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalLedgerTest {
    @TempDir Path directory;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    Path file() { return directory.resolve("wallet.wal"); }

    @Test void setIsAtomicDurableAndIdempotentIncludingZero() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            ledger.change(alice, "Alice", "gold", new BigDecimal("10"), true, "seed", "");
            var set = ledger.setBalance(alice, "Alice", "gold", new BigDecimal("25"), "set-one", "admin");
            assertEquals("SET", set.type());
            ledger.change(alice, "Alice", "gold", new BigDecimal("3"), true, "later", "");
            assertEquals(set, ledger.setBalance(alice, "Alice", "gold", new BigDecimal("25"), "set-one", "retry"));
            assertEquals(new BigDecimal("28"), ledger.balance(alice, "gold"));
            assertThrows(WalletException.class, () -> ledger.setBalance(alice, "Alice", "gold", BigDecimal.ZERO, "set-one", "collision"));
            assertThrows(WalletException.class, () -> ledger.change(alice, "Alice", "gold", new BigDecimal("25"), true, "set-one", "collision"));
            ledger.setBalance(alice, "Alice", "gold", BigDecimal.ZERO, "set-zero", "admin");
        }
        try (var ledger = new LocalLedger(file())) {
            assertEquals(0, ledger.balance(alice, "gold").signum());
            assertEquals(4, ledger.transactions(alice, List.of("gold"), 1, 45).total());
            assertEquals("SET", ledger.transactions(alice, List.of("gold"), 1, 45).records().getFirst().type());
        }
    }

    @Test void setCannotBypassImportLockNegativeAmountOrReservedBusinessNumber() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            assertThrows(WalletException.class, () -> ledger.setBalance(alice, "Alice", "gold", BigDecimal.ONE.negate(), "bad", ""));
            assertThrows(WalletException.class, () -> ledger.setBalance(alice, "Alice", "gold", BigDecimal.ONE, "site-import:x", ""));
            ledger.beginImport();
            assertThrows(WalletException.class, () -> ledger.setBalance(alice, "Alice", "gold", BigDecimal.TEN, "during", ""));
            ledger.endImport();
            assertEquals(0, ledger.balance(alice, "gold").signum());
        }
    }

    @Test void concurrentSetsAndCreditsEachPersistOneWholeTransaction() throws Exception {
        try (var ledger = new LocalLedger(file()); var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 40; i++) {
                final int n = i;
                tasks.add(pool.submit(() -> {
                    if (n % 2 == 0) ledger.setBalance(alice, "Alice", "gold", BigDecimal.TEN, "set-" + n, "");
                    else ledger.change(alice, "Alice", "gold", BigDecimal.ONE, true, "add-" + n, "");
                }));
            }
            for (var task : tasks) task.get();
            var latest = ledger.transactions(alice, List.of("gold"), 1, 45);
            assertEquals(40, latest.total());
            assertEquals(latest.records().getFirst().balanceAfter(), ledger.balance(alice, "gold"));
        }
    }
    LocalLedger.Imported row(UUID id, String amount) {
        return new LocalLedger.Imported(id, "player", "coin", new BigDecimal(amount), id.toString());
    }

    @Test void durableIdempotencyAndHistoryAreIsolatedByPlayerAndCurrency() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            var first = ledger.change(alice, "Alice", "coin", new BigDecimal("20.00"), true, "one", "seed");
            assertEquals(first, ledger.change(alice, "Alice", "coin", new BigDecimal("20.00"), true, "one", "retry"));
            assertThrows(WalletException.class, () -> ledger.change(bob, "Bob", "coin", BigDecimal.ONE, true, "one", "collision"));
            ledger.change(bob, "Bob", "coin", new BigDecimal("4.00"), true, "two", "seed");
            ledger.change(alice, "Alice", "gem", new BigDecimal("3.00"), true, "three", "seed");
            assertEquals(1, ledger.transactions(alice, List.of("coin"), 1, 45).total());
            assertThrows(WalletException.class, () -> ledger.change(alice, "Alice", "coin", new BigDecimal("21.00"), false, "bad", "overdraw"));
        }
        try (var ledger = new LocalLedger(file())) {
            assertEquals(new BigDecimal("20.00"), ledger.balance(alice, "coin"));
            assertEquals(new BigDecimal("4.00"), ledger.balance(bob, "coin"));
            ledger.change(alice, "Alice", "coin", new BigDecimal("20.00"), true, "one", "restart retry");
            assertEquals(new BigDecimal("20.00"), ledger.balance(alice, "coin"));
        }
    }

    @Test void importAndPermanentMarkerCommitTogetherAndSurviveRestart() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            ledger.change(alice, "Alice", "coin", new BigDecimal("2.00"), true, "existing", "seed");
            ledger.beginImport();
            assertFalse(ledger.available());
            assertFalse(ledger.needsRecovery());
            assertThrows(WalletException.class, () -> ledger.change(bob, "Bob", "coin", BigDecimal.ONE, true, "during", "blocked"));
            ledger.importOnce("https://old.example", List.of(row(alice, "10.00"), row(bob, "0.00")));
            ledger.endImport();
            assertEquals(new BigDecimal("12.00"), ledger.balance(alice, "coin"));
        }
        try (var ledger = new LocalLedger(file())) {
            assertTrue(ledger.migration().complete());
            assertEquals(2, ledger.migration().rows());
            assertThrows(WalletException.class, ledger::beginImport);
            assertEquals(new BigDecimal("12.00"), ledger.balance(alice, "coin"));
        }
    }

    @Test void validationFailureDoesNotPartiallyImportOrConsumeTheMarker() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            ledger.beginImport();
            assertThrows(WalletException.class, () -> ledger.importOnce("old", List.of(row(alice, "5.00"), row(alice, "7.00"))));
            ledger.endImport();
            assertEquals(0, ledger.balance(alice, "coin").signum());
            assertFalse(ledger.migration().complete());
            ledger.beginImport();
            ledger.importOnce("old", List.of(row(alice, "5.00")));
            ledger.endImport();
        }
    }

    @Test void interruptedTailHasNeitherPartialFundsNorAPartialMigrationMarker() throws Exception {
        long committed;
        try (var ledger = new LocalLedger(file())) {
            ledger.change(alice, "Alice", "coin", new BigDecimal("2.00"), true, "seed", "seed");
            committed = Files.size(file());
            ledger.beginImport(); ledger.importOnce("old", List.of(row(alice, "10.00"), row(bob, "8.00"))); ledger.endImport();
        }
        try (var channel = FileChannel.open(file(), StandardOpenOption.WRITE)) { channel.truncate(committed + 20); }
        try (var ledger = new LocalLedger(file())) {
            assertEquals(new BigDecimal("2.00"), ledger.balance(alice, "coin"));
            assertEquals(0, ledger.balance(bob, "coin").signum());
            assertFalse(ledger.migration().complete());
            assertEquals(committed, Files.size(file()));
        }
    }

    @Test void corruptionFailsClosedAndDoesNotResetTheLedger() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            ledger.change(alice, "Alice", "coin", BigDecimal.TEN, true, "seed", "seed");
        }
        byte[] bytes = Files.readAllBytes(file()); bytes[bytes.length - 1] ^= 1; Files.write(file(), bytes);
        assertThrows(java.io.IOException.class, () -> new LocalLedger(file()));
        assertArrayEquals(bytes, Files.readAllBytes(file()));
    }

    @Test void internalBusinessNumbersCannotCollideWithPublicApiWrites() throws Exception {
        try (var ledger = new LocalLedger(file())) {
            assertThrows(WalletException.class, () -> ledger.change(alice, "Alice", "coin", BigDecimal.ONE,
                    true, "site-import:" + alice + "|coin", "collision"));
            assertThrows(WalletException.class, () -> ledger.change(alice, "Alice", "coin", BigDecimal.ONE,
                    true, "account:" + alice + ":coin", "collision"));
            ledger.createAccount(alice, "Alice", "coin");
            ledger.beginImport(); ledger.importOnce("old", List.of(row(alice, "10.00"))); ledger.endImport();
        }
        try (var ledger = new LocalLedger(file())) {
            assertTrue(ledger.migration().complete());
            assertEquals(new BigDecimal("10.00"), ledger.balance(alice, "coin"));
        }
    }

    @Test void concurrentVaultAndApiDebitsCannotOverdraw() throws Exception {
        try (var ledger = new LocalLedger(file()); var pool = Executors.newFixedThreadPool(4)) {
            ledger.change(alice, "Alice", "coin", new BigDecimal("50.00"), true, "seed", "seed");
            var successes = new AtomicInteger();
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 100; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    try { ledger.change(alice, "Alice", "coin", BigDecimal.ONE, false, "debit-" + id, ""); successes.incrementAndGet(); }
                    catch (WalletException ignored) { }
                }));
            }
            for (var future : futures) future.get();
            assertEquals(50, successes.get());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }
}
