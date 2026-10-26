package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultLedgerTest {
    @TempDir Path directory;
    final UUID account = UUID.randomUUID();

    private VaultLedger.Entry entry(String key, boolean credit) {
        return new VaultLedger.Entry(key, "vault:provider", account, 1250, credit, VaultLedger.State.PENDING);
    }

    @Test void successfulDebitAndCreditReplayAfterRestartWithoutCallingEconomyAgain() throws Exception {
        Path file = directory.resolve("vault.bin");
        AtomicInteger calls = new AtomicInteger();
        VaultLedger first = new VaultLedger(file);
        for (boolean credit : List.of(false, true)) {
            var request = entry(credit ? "credit" : "debit", credit);
            first.execute(request, () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(null); }, Runnable::run).join();
            new VaultLedger(file).execute(request, () -> { fail("成功记录不得再次调用提供者"); return null; }, Runnable::run).join();
        }
        assertEquals(2, calls.get());
        assertTrue(new VaultLedger(file).pending().isEmpty());
    }

    @Test void unknownResultIsNeverAutomaticallyRetriedEvenAfterRestart() throws Exception {
        Path file = directory.resolve("vault.bin");
        VaultLedger first = new VaultLedger(file);
        AtomicInteger charged = new AtomicInteger();
        assertThrows(CompletionException.class, () -> first.execute(entry("debit", false), () -> {
            charged.incrementAndGet(); return CompletableFuture.failedFuture(new IOException("提供者已扣款但丢失回复"));
        }, Runnable::run).join());
        VaultLedger restored = new VaultLedger(file);
        assertThrows(CompletionException.class, () -> restored.execute(entry("debit", false), () -> {
            fail("结果不明不能自动重扣"); return null;
        }, Runnable::run).join());
        assertEquals(VaultLedger.State.PENDING, restored.pending().getFirst().state());
        restored.resolve("debit", VaultLedger.Resolution.SUCCESS);
        restored.execute(entry("debit", false), () -> { fail(); return null; }, Runnable::run).join();
        assertEquals(1, charged.get());
    }

    @Test void confirmedNoCallCanRetryButRejectedTransferRequiresExplicitResolution() throws Exception {
        VaultLedger ledger = new VaultLedger(directory.resolve("vault.bin"));
        assertThrows(CompletionException.class, () -> ledger.execute(entry("debit", false),
                () -> CompletableFuture.failedFuture(new VaultLedger.NotCalled("玩家在调用前离线")), Runnable::run).join());
        assertTrue(ledger.pending().isEmpty());
        assertThrows(CompletionException.class, () -> ledger.execute(entry("debit", false),
                () -> CompletableFuture.failedFuture(new VaultLedger.Rejected("余额不足")), Runnable::run).join());
        assertEquals(VaultLedger.State.REJECTED, ledger.pending().getFirst().state());
        assertThrows(CompletionException.class, () -> ledger.execute(entry("debit", false), () -> { fail(); return null; }, Runnable::run).join());
        ledger.resolve("debit", VaultLedger.Resolution.RETRY);
        ledger.execute(entry("debit", false), () -> CompletableFuture.completedFuture(null), Runnable::run).join();
        assertThrows(IllegalArgumentException.class, () -> ledger.resolve("debit", VaultLedger.Resolution.RETRY));
    }

    @Test void cannotChangeBusinessNumberAccountAmountOrProvider() throws Exception {
        VaultLedger ledger = new VaultLedger(directory.resolve("vault.bin"));
        ledger.execute(entry("debit", false), () -> CompletableFuture.completedFuture(null), Runnable::run).join();
        var providerChanged = new VaultLedger.Entry("debit", "vault:other", account, 1250, false, VaultLedger.State.PENDING);
        assertThrows(CompletionException.class, () -> ledger.execute(providerChanged, () -> { fail(); return null; }, Runnable::run).join());
        final var accountChanged = new VaultLedger.Entry("debit", "vault:provider", UUID.randomUUID(), 1250, false, VaultLedger.State.PENDING);
        assertThrows(CompletionException.class, () -> ledger.execute(accountChanged, () -> { fail(); return null; }, Runnable::run).join());
        final var amountChanged = new VaultLedger.Entry("debit", "vault:provider", account, 1251, false, VaultLedger.State.PENDING);
        assertThrows(CompletionException.class, () -> ledger.execute(amountChanged, () -> { fail(); return null; }, Runnable::run).join());
    }

    @Test void cannotResolveOrDuplicateAnInflightProviderCall() throws Exception {
        VaultLedger ledger = new VaultLedger(directory.resolve("vault.bin"));
        CompletableFuture<Void> provider = new CompletableFuture<>();
        var inFlight = ledger.execute(entry("credit", true), () -> provider, Runnable::run);
        assertThrows(IllegalArgumentException.class, () -> ledger.resolve("credit", VaultLedger.Resolution.RETRY));
        assertThrows(CompletionException.class, () -> ledger.execute(entry("credit", true), () -> { fail(); return null; }, Runnable::run).join());
        provider.complete(null); inFlight.join();
        assertTrue(ledger.pending().isEmpty());
    }

    @Test void unreadableOrUnwritableLedgerNeverPermitsUnrecordedMoneyMovement() throws Exception {
        Path file = directory.resolve("vault.bin");
        VaultLedger ledger = new VaultLedger(file); Files.createDirectory(file);
        assertThrows(CompletionException.class, () -> ledger.execute(entry("debit", false), () -> { fail(); return null; }, Runnable::run).join());
        Files.write(directory.resolve("bad.bin"), new byte[]{0,1,2,3});
        assertThrows(IOException.class, () -> new VaultLedger(directory.resolve("bad.bin")));
    }

    @Test void outerPaymentRecoveryAndUndoUseVaultReceiptsExactlyOnce() throws Exception {
        Path file = directory.resolve("vault.bin"), payments = directory.resolve("payments.bin");
        VaultLedger ledger = new VaultLedger(file);
        AtomicInteger balance = new AtomicInteger(10_000), calls = new AtomicInteger();
        DurablePayments.Wallet adapter = new DurablePayments.Wallet() {
            public CompletableFuture<Void> debit(Payment p) {
                return ledger.execute(new VaultLedger.Entry(p.debitKey(), "vault:provider", account, p.cents(), false, VaultLedger.State.PENDING), () -> {
                    balance.addAndGet(-(int)p.cents()); calls.incrementAndGet(); return CompletableFuture.completedFuture(null);
                }, Runnable::run);
            }
            public CompletableFuture<Void> credit(Payment p, boolean refund) {
                return ledger.execute(new VaultLedger.Entry(p.refundKey(), "vault:provider", account, p.cents(), true, VaultLedger.State.PENDING), () -> {
                    balance.addAndGet((int)p.cents()); calls.incrementAndGet(); return CompletableFuture.completedFuture(null);
                }, Runnable::run);
            }
            public boolean rejected(Throwable error) { return error instanceof VaultLedger.Rejected; }
        };
        DurablePayments engine = new DurablePayments(new PaymentJournal(payments), adapter, Runnable::run, error -> fail(error));
        engine.handler("move", ignored -> DurablePayments.Applied.YES);
        Payment p = Payment.create("move", account, "Player", "vault:provider", 1250, null, "", 0, Map.of());
        engine.submit(p, null); engine.recover();
        assertEquals(8750, balance.get());
        engine.refundAfterRollback(p.id()); engine.refundAfterRollback(p.id()); engine.recover();
        assertEquals(10_000, balance.get()); assertEquals(2, calls.get());
        assertEquals(Payment.Stage.REFUNDED, new PaymentJournal(payments).get(p.id()).stage());
    }

    @Test void amountPrecisionAndResponseAmountCannotSilentlyRound() {
        assertEquals(12.50, VaultMoney.amount(1250, 2));
        assertEquals(12.50, VaultMoney.amount(1250, -1));
        assertThrows(VaultLedger.Rejected.class, () -> VaultMoney.amount(1250, 0));
        assertThrows(VaultLedger.Rejected.class, () -> VaultMoney.amount(1251, 1));
        assertThrows(VaultLedger.Rejected.class, () -> VaultMoney.amount(Long.MAX_VALUE, 2));
        assertFalse(VaultMoney.matches(Double.NaN, 1250));
        assertFalse(VaultMoney.matches(12.49, 1250));
        assertTrue(VaultMoney.matches(12.50, 1250));
    }

    @Test void paymentProviderTagPersistsAndCannotBeRebound() throws Exception {
        Payment p = Payment.create("donate", account, "Player", "vault:provider", 100, null, "", 0, Map.of("town", "example"));
        p = p.withEconomy("vault:provider");
        PaymentJournal journal = new PaymentJournal(directory.resolve("payments.bin")); journal.insert(p);
        Payment restored = new PaymentJournal(directory.resolve("payments.bin")).get(p.id());
        assertEquals("vault:provider", restored.payload().get("_economy"));
        assertEquals("example", restored.payload().get("town"));
        assertThrows(IllegalArgumentException.class, () -> restored.withEconomy("wallet"));
    }
}
