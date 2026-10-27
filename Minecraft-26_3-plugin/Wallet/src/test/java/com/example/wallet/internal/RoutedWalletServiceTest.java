package com.example.wallet.internal;

import static org.junit.jupiter.api.Assertions.*;
import com.example.wallet.WalletConfig;
import com.example.wallet.api.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RoutedWalletServiceTest {
    final UUID player = UUID.randomUUID();
    final List<String> calls = new ArrayList<>();
    boolean websiteOffline;

    WalletConfig config() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                backend: hybrid
                currencies:
                  gold: {source: local, scale: 0}
                  crystal: {source: website, website-code: EMERALD, scale: 0}
                vault: {currency: gold}
                """);
        return WalletConfig.load(yaml);
    }
    WalletTransaction tx(String code, int index, long at) {
        return new WalletTransaction(code + index, "biz-" + code + index, "CREDIT", code.equals("gold") ? "LOCAL" : "SITE",
                code, "IN", BigDecimal.ONE, BigDecimal.TEN, "", at);
    }
    WalletService backend(String code, int scale, int count) {
        return (WalletService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WalletService.class}, (p, m, a) -> {
            calls.add(code + ":" + m.getName() + (a != null && a.length > 2 ? ":" + a[2] : ""));
            if (websiteOffline && code.equals("EMERALD")) return CompletableFuture.failedFuture(new WalletException(503, "offline"));
            var asset = new WalletAsset(code, code, code, scale, true, true, true);
            if (m.getName().equals("assets")) return CompletableFuture.completedFuture(List.of(asset));
            if (m.getName().equals("cachedAssets")) return List.of(asset);
            if (m.getName().equals("balance") || m.getName().equals("cachedBalance")) {
                assertEquals(code, a[2 == a.length ? 1 : 2]);
                var balance = new WalletBalance("user", "Player", player.toString(), code, BigDecimal.TEN, 1);
                return m.getName().equals("balance") ? CompletableFuture.completedFuture(balance) : balance;
            }
            if (List.of("credit", "debit", "setBalance").contains(m.getName())) {
                assertEquals(code, a[2]);
                return CompletableFuture.completedFuture(tx(code, 0, 1));
            }
            if (m.getName().equals("transactions")) {
                assertEquals(List.of(code), a[2]);
                int page = (int) a[3], size = (int) a[4];
                var rows = new ArrayList<WalletTransaction>();
                int from = Math.min((page - 1) * size, count);
                for (int i = from; i < Math.min(from + size, count); i++) rows.add(tx(code, i, 10000 - 2 * i - (code.equals("gold") ? 0 : 1)));
                return CompletableFuture.completedFuture(new WalletTransactionPage(rows, count));
            }
            throw new AssertionError(m.getName());
        });
    }
    RoutedWalletService routed(int localCount, int siteCount) throws Exception {
        return new RoutedWalletService(config(), backend("gold", 0, localCount), backend("EMERALD", 0, siteCount));
    }

    @Test void balancesAndAdjustmentsUseOnlyTheirConfiguredSourceAndRemapAliases() throws Exception {
        var wallet = routed(0, 0);
        assertEquals("gold", wallet.balance(player, "Player", "GOLD").join().assetCode());
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("EMERALD")));
        calls.clear();
        assertEquals("crystal", wallet.balance(player, "Player", "crystal").join().assetCode());
        assertEquals("crystal", wallet.credit(player, "Player", "crystal", BigDecimal.TEN, "in", "").join().assetCode());
        wallet.debit(player, "Player", "crystal", BigDecimal.ONE, "out", "").join();
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("gold")));
        calls.clear();
        wallet.setBalance(player, "Player", "gold", BigDecimal.ZERO, "zero", "").join();
        assertEquals(List.of("gold:setBalance:gold"), calls);
        calls.clear();
        assertThrows(RuntimeException.class, () -> wallet.setBalance(player, "Player", "crystal", BigDecimal.ZERO, "bad", "").join());
        assertTrue(calls.isEmpty(), "website set must fail before any remote call");
    }

    @Test void websiteOutageNeverRedirectsMoneyAndLocalOperationsStillWork() throws Exception {
        var wallet = routed(0, 0); websiteOffline = true;
        assertThrows(RuntimeException.class, () -> wallet.credit(player, "Player", "crystal", BigDecimal.TEN, "bad", "").join());
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("gold")));
        calls.clear();
        wallet.credit(player, "Player", "gold", BigDecimal.TEN, "good", "").join();
        wallet.balance(player, "Player", "gold").join();
        assertTrue(calls.stream().allMatch(c -> c.startsWith("gold")));
    }

    @Test void unknownOrWrongPrecisionRequestsNeverReachEitherSource() throws Exception {
        var wallet = routed(0, 0);
        assertThrows(RuntimeException.class, () -> wallet.credit(player, "Player", "typo", BigDecimal.ONE, "bad", "").join());
        assertThrows(RuntimeException.class, () -> wallet.credit(player, "Player", "crystal", new BigDecimal("0.1"), "bad", "").join());
        assertTrue(calls.isEmpty());
    }

    @Test void mergedHistoryPaginatesAcrossBackendPagesWithoutDuplicatesOrGaps() throws Exception {
        var wallet = routed(260, 260);
        final var all = new ArrayList<WalletTransaction>();
        for (int page = 1; page <= 3; page++) {
            var result = wallet.transactions(player, "Player", null, page, 200).join();
            assertEquals(520, result.total()); all.addAll(result.records());
        }
        assertEquals(520, all.size());
        assertEquals(520, all.stream().map(WalletTransaction::id).distinct().count());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(10000 - i, all.get(i).createdAt());
            assertEquals(i % 2 == 0 ? "gold" : "crystal", all.get(i).assetCode());
        }
    }

    @Test void localHistoryFilterAndEmptySelectionDoNotQueryWebsite() throws Exception {
        var wallet = routed(3, 3);
        assertEquals(0, wallet.transactions(player, "Player", List.of(), 1, 20).join().total());
        assertTrue(calls.isEmpty());
        assertEquals(3, wallet.transactions(player, "Player", List.of("GOLD"), 1, 20).join().total());
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("EMERALD")));
    }

    @Test void sitePrecisionMismatchDisablesOnlyTheSiteCurrency() throws Exception {
        var wallet = new RoutedWalletService(config(), backend("gold", 0, 0), backend("EMERALD", 2, 0));
        var assets = wallet.assets().join();
        assertTrue(assets.getFirst().enabled()); assertFalse(assets.getLast().enabled());
        wallet.credit(player, "Player", "gold", BigDecimal.TEN, "okay", "").join();
        assertThrows(RuntimeException.class, () -> wallet.credit(player, "Player", "crystal", BigDecimal.ONE, "bad", "").join());
    }
}
