package com.example.wallet.local;

import static org.junit.jupiter.api.Assertions.*;
import com.example.wallet.api.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteMigrationTest {
    @TempDir Path directory;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    final List<WalletAsset> assets = List.of(new WalletAsset("coin", "金币", "金币", 2, true, true, true));
    WalletService source(Function<UUID, WalletBalance> balances) {
        return (WalletService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WalletService.class}, (proxy, method, args) -> {
            if (method.getName().equals("assets")) return CompletableFuture.completedFuture(assets);
            if (method.getName().equals("balance")) {
                try { return CompletableFuture.completedFuture(balances.apply((UUID) args[0])); }
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

    @Test void websiteCannotReturnAnotherGameAccountsBalance() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> balance(bob, "another-user"));
            assertThrows(WalletException.class, () -> SiteMigration.execute(ledger, assets, remote, players(), "old"));
            assertFalse(ledger.migration().complete());
            assertEquals(0, ledger.balance(alice, "coin").signum());
        }
    }

    @Test void explicitUnboundPlayersAreReportedWithoutBeingGivenFakeBalances() throws Exception {
        try (var ledger = new LocalLedger(directory.resolve("wallet.wal"))) {
            var remote = source(id -> { if (id.equals(bob)) throw new WalletException(400, "玩家未绑定网站用户"); return balance(id, "one"); });
            var result = SiteMigration.execute(ledger, assets, remote, players(), "old");
            assertEquals(1, result.players()); assertEquals(1, result.skipped());
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
