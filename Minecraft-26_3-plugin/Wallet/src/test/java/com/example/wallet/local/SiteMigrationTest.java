package com.example.wallet.local;

import static org.junit.jupiter.api.Assertions.*;
import com.example.wallet.api.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteMigrationTest {
    @TempDir Path directory;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    final List<WalletAsset> assets = List.of(new WalletAsset("coin", "金币", "金币", 2, true, true, true));
    WalletService source(Function<UUID, WalletBalance> balances) {
        return source(assets, (id, code) -> balances.apply(id));
    }
    WalletService source(List<WalletAsset> remoteAssets, BiFunction<UUID, String, WalletBalance> balances) {
        return (WalletService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WalletService.class}, (proxy, method, args) -> {
            if (method.getName().equals("assets")) return CompletableFuture.completedFuture(remoteAssets);
            if (method.getName().equals("balance")) {
                try { return CompletableFuture.completedFuture(balances.apply((UUID) args[0], (String) args[2])); }
                catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
            }
            throw new AssertionError("Migration must never write the website: " + method.getName());
        });
    }
    WalletBalance balance(UUID id, String user) {
        return new WalletBalance(user, "Player", id.toString(), "coin", new BigDecimal("12.34"), 1);
    }
    List<SiteMigration.Candidate> players() {
        return List.of(new SiteMigration.Candidate(alice, "Alice"), new SiteMigration.Candidate(bob, "Bob"));
    }

    @Test void successfulReadOnlyWebsiteSnapshotImportsExactlyOnce() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> balance(id, id.toString()));
            var result = SiteMigration.execute(ledger, assets, remote, players(), "old");
            assertEquals(2, result.players());
            assertEquals(new BigDecimal("12.34"), ledger.balance(alice, "coin"));
            assertThrows(WalletException.class, () -> SiteMigration.execute(ledger, assets, remote, players(), "old"));
            assertEquals(new BigDecimal("12.34"), ledger.balance(alice, "coin"));
        }
    }

    @Test void lateHttpFailureNeverCommitsEarlierPlayersAndCanBeRetried() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> { if (id.equals(bob)) throw new WalletException(503, "服务不可用"); return balance(id, "one"); });
            assertThrows(RuntimeException.class, () -> SiteMigration.execute(ledger, assets, remote, players(), "old"));
            assertFalse(ledger.migration().complete());
            assertTrue(ledger.available());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }

    @Test void ambiguousWebsiteIdentityCannotMultiplyOneBalanceAcrossGameAccounts() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> balance(id, "same-site-user"));
            assertThrows(WalletException.class, () -> SiteMigration.execute(ledger, assets, remote, players(), "old"));
            assertFalse(ledger.migration().complete());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }

    @Test void mismatchedPlayerIsReportedAndSkippedWhileFollowingPlayerImportsExactlyOnce() throws Exception {
        final var warnings = new ArrayList<String>();
        final var file = directory.resolve("wallet.wal");
        final var remote = source(id -> balance(bob, "bob-site-user"));
        try (var ledger = new LocalLedger(file)) {
            var result = SiteMigration.execute(ledger, assets, remote, players(), "old", warnings::add);
            assertEquals(1, result.players());
            assertEquals(1, result.skipped());
            assertEquals(1, result.mismatched());
            assertEquals(1, result.migration().rows());
            assertEquals(0, ledger.balance(alice, "coin").signum());
            assertEquals(0, ledger.transactions(alice, null, 1, 45).total());
            assertEquals(new BigDecimal("12.34"), ledger.balance(bob, "coin"));
            assertEquals(1, warnings.size());
            for (String detail : List.of("Alice", alice.toString(), bob.toString(), "bob-site-user", "coin", "Player")) {
                assertTrue(warnings.getFirst().contains(detail), detail);
            }
        }
        try (var ledger = new LocalLedger(file)) {
            assertTrue(ledger.migration().complete());
            assertEquals(0, ledger.balance(alice, "coin").signum());
            assertEquals(new BigDecimal("12.34"), ledger.balance(bob, "coin"));
            assertThrows(WalletException.class,
                    () -> SiteMigration.execute(ledger, assets, remote, players(), "old", warnings::add));
            assertEquals(1, warnings.size());
        }
    }

    @Test void mismatchAfterAcceptedPlayerDoesNotDiscardAcceptedBalanceOrChangeExistingSkippedBalance() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            ledger.change(bob, "Bob", "coin", new BigDecimal("5.00"), true, "seed", "existing funds");
            var result = SiteMigration.execute(ledger, assets, source(id -> balance(alice, "alice-site-user")), players(), "old");
            assertEquals(1, result.players());
            assertEquals(1, result.skipped());
            assertEquals(1, result.mismatched());
            assertEquals(new BigDecimal("12.34"), ledger.balance(alice, "coin"));
            assertEquals(new BigDecimal("5.00"), ledger.balance(bob, "coin"));
            assertEquals(1, ledger.transactions(bob, null, 1, 45).total());
        }
    }

    @Test void allMismatchedPlayersLeaveMigrationIncompleteAndAllowRetry() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            final var warnings = new ArrayList<String>();
            assertThrows(WalletException.class, () -> SiteMigration.execute(ledger, assets,
                    source(id -> balance(UUID.randomUUID(), id.toString())), players(), "old", warnings::add));
            assertEquals(2, warnings.size());
            assertFalse(ledger.migration().complete());
            assertTrue(ledger.available());
            assertEquals(0, ledger.balance(alice, "coin").signum());
            assertEquals(0, ledger.balance(bob, "coin").signum());
            assertEquals(2, SiteMigration.execute(ledger, assets,
                    source(id -> balance(id, id.toString())), players(), "retry").players());
        }
    }

    @Test void lateCurrencyMismatchSkipsWholePlayerAndDoesNotReserveTheirWebsiteIdentity() throws Exception {
        final var multiple = List.of(assets.getFirst(), new WalletAsset("gem", "宝石", "宝石", 2, true, true, true));
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(multiple, (id, code) -> new WalletBalance("shared-site-user", "Player",
                    (id.equals(alice) && code.equals("gem") ? bob : id).toString(), code, new BigDecimal("12.34"), 1));
            var result = SiteMigration.execute(ledger, multiple, remote, players(), "old");
            assertEquals(1, result.players());
            assertEquals(1, result.mismatched());
            assertEquals(2, result.migration().rows());
            assertEquals(0, ledger.transactions(alice, null, 1, 45).total());
            for (String code : List.of("coin", "gem")) {
                assertEquals(0, ledger.balance(alice, code).signum());
                assertEquals(new BigDecimal("12.34"), ledger.balance(bob, code));
            }
        }
    }

    @Test void lateUnboundCurrencyAlsoDiscardsWholePlayerAndDoesNotReserveTheirWebsiteIdentity() throws Exception {
        final var multiple = List.of(assets.getFirst(), new WalletAsset("gem", "宝石", "宝石", 2, true, true, true));
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(multiple, (id, code) -> {
                if (id.equals(alice) && code.equals("gem")) throw new WalletException(400, "玩家未绑定网站用户");
                return new WalletBalance("shared-site-user", "Player", id.toString(), code, new BigDecimal("12.34"), 1);
            });
            var result = SiteMigration.execute(ledger, multiple, remote, players(), "old");
            assertEquals(1, result.players());
            assertEquals(1, result.skipped());
            assertEquals(0, result.mismatched());
            assertEquals(2, result.migration().rows());
            assertEquals(0, ledger.transactions(alice, null, 1, 45).total());
        }
    }

    @Test void mismatchWarningIsEmittedEvenWhenLaterHttpFailureAbortsTheBatch() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            final var warnings = new ArrayList<String>();
            var remote = source(id -> {
                if (id.equals(bob)) throw new WalletException(503, "服务不可用");
                return balance(bob, "bob-site-user");
            });
            assertThrows(RuntimeException.class,
                    () -> SiteMigration.execute(ledger, assets, remote, players(), "old", warnings::add));
            assertEquals(1, warnings.size());
            assertFalse(ledger.migration().complete());
            assertTrue(ledger.available());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }

    @Test void explicitUnboundPlayersAreReportedWithoutBeingGivenFakeBalances() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> { if (id.equals(bob)) throw new WalletException(400, "玩家未绑定网站用户"); return balance(id, "one"); });
            var result = SiteMigration.execute(ledger, assets, remote, players(), "old");
            assertEquals(1, result.players()); assertEquals(1, result.skipped());
            assertEquals(0, result.mismatched());
            assertEquals(0, ledger.balance(bob, "coin").signum());
        }
    }

    @Test void unrelatedBindingErrorsMustAbortInsteadOfSkippingAnAccount() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> {
                if (id.equals(bob)) throw new WalletException(403, "API Key 未绑定插件");
                return balance(id, "one");
            });
            assertThrows(RuntimeException.class, () -> SiteMigration.execute(ledger, assets, remote, players(), "old"));
            assertFalse(ledger.migration().complete());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }
}
