package com.example.wallet.local;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/** Local multi-currency service shared by WalletApi and the synchronous Vault adapter. */
public final class LocalWalletService implements WalletService, AutoCloseable {
    private final Plugin plugin;
    private final List<WalletAsset> assets;
    private final LocalLedger ledger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "Wallet-Local"); thread.setDaemon(true); return thread;
    });

    public LocalWalletService(final Plugin plugin, final WalletConfig config) throws IOException {
        this.plugin = plugin;
        assets = config.localCurrencies();
        ledger = new LocalLedger(plugin.getDataFolder().toPath().resolve("wallet-ledger.wal"));
    }

    public LocalLedger ledger() { return ledger; }
    public WalletAsset asset(final String code) {
        return assets.stream().filter(a -> a.code().equalsIgnoreCase(code) && a.enabled()).findFirst()
                .orElseThrow(() -> new WalletException(0, "币种不存在或未启用：" + code));
    }
    public BigDecimal amount(final String code, final BigDecimal value) {
        final WalletAsset asset = asset(code);
        try {
            final BigDecimal result = value.setScale(asset.scale(), RoundingMode.UNNECESSARY);
            if (result.signum() < 0 || result.compareTo(new BigDecimal("1000000000000000")) > 0) {
                throw new ArithmeticException("range");
            }
            return result;
        } catch (ArithmeticException | NullPointerException error) {
            throw new WalletException(0, "金额超出范围或不符合 " + asset.scale() + " 位小数精度");
        }
    }
    public WalletTransaction changeNow(final UUID player, final String name, final String code,
            final BigDecimal amount, final boolean credit, final String businessNo, final String remark) {
        final WalletAsset asset = asset(code);
        final BigDecimal value = amount(code, amount);
        return ledger.change(player, name, asset.code(), value, credit, businessNo, remark);
    }
    @Override public CompletableFuture<List<WalletAsset>> assets() { return CompletableFuture.completedFuture(assets); }
    @Override public List<WalletAsset> cachedAssets() { return assets; }
    @Override public CompletableFuture<WalletBalance> balance(UUID player, String name, String code) {
        return async(() -> snapshot(player, name, asset(code).code()));
    }
    @Override public WalletBalance cachedBalance(UUID player, String code) {
        return snapshot(player, "", asset(code).code());
    }
    private WalletBalance snapshot(UUID player, String name, String code) {
        return new WalletBalance("local-" + player, name, player.toString(), code,
                ledger.balance(player, code), System.currentTimeMillis());
    }
    @Override public CompletableFuture<WalletTransaction> credit(UUID player, String name, String code,
            BigDecimal amount, String businessNo, String remark) {
        return async(() -> changeNow(player, name, code, amount, true, businessNo, remark));
    }
    @Override public CompletableFuture<WalletTransaction> debit(UUID player, String name, String code,
            BigDecimal amount, String businessNo, String remark) {
        return async(() -> changeNow(player, name, code, amount, false, businessNo, remark));
    }
    @Override public CompletableFuture<WalletTransaction> setBalance(UUID player, String name, String code,
            BigDecimal amount, String businessNo, String remark) {
        return async(() -> ledger.setBalance(player, name, asset(code).code(), amount(code, amount), businessNo, remark));
    }
    @Override public CompletableFuture<WalletTransactionPage> transactions(UUID player, String name, int page, int size) {
        return transactions(player, name, null, page, size);
    }
    @Override public CompletableFuture<WalletTransactionPage> transactions(UUID player, String name,
            List<String> codes, int page, int size) {
        final List<String> normalized = codes == null ? null
                : codes.stream().map(code -> code.toLowerCase(Locale.ROOT)).toList();
        return async(() -> ledger.transactions(player, normalized, page, size));
    }
    private <T> CompletableFuture<T> async(final Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException error) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
        finally {
            try { ledger.close(); }
            catch (IOException error) { plugin.getLogger().severe("钱包账本关闭失败：" + error.getClass().getSimpleName()); }
        }
    }
}
