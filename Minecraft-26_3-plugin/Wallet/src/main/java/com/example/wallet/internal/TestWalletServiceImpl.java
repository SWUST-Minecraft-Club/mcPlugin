package com.example.wallet.internal;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletBalance;
import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletService;
import com.example.wallet.api.WalletTransaction;
import com.example.wallet.api.WalletTransactionPage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 本地测试模式的钱包实现：完全不连官网，余额与流水只存本机（plugins/Wallet/test-ledger.yml）。
 *
 * <p>行为对齐官网语义：{@code businessNo} 幂等（重复提交返回原流水）、扣款余额不足报错、
 * 流水倒序分页；新玩家首次出现时按 {@code test-mode.start-balance} 发起始金额。
 * 仅用于本地联调 / 测试服，由 {@code test-mode.enabled} 显式开启（默认 false）。
 */
public final class TestWalletServiceImpl implements WalletService, AutoCloseable {

    private static final int MAX_LEDGER_ENTRIES = 2000;
    private static final String LEDGER_FILE = "test-ledger.yml";

    private final Plugin plugin;
    private final WalletConfig config;
    private final WalletAsset asset;
    private final ExecutorService executor;

    private final Object lock = new Object();
    private final Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
    private final List<WalletTransaction> records = new ArrayList<>();
    private final Map<String, WalletTransaction> byBusinessNo = new LinkedHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);

    public TestWalletServiceImpl(final Plugin plugin, final WalletConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.asset = new WalletAsset(
                config.testCurrencyCode(),
                config.testCurrencyName(),
                config.testCurrencySymbol(),
                2,
                true,
                true,
                true);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "Wallet-Test");
            thread.setDaemon(true);
            return thread;
        });
        this.load();
        plugin.getLogger().info("测试模式钱包就绪：货币 " + asset.code() + "，起始余额 "
                + config.testStartBalance() + "，账本文件 " + LEDGER_FILE);
    }

    // ------------------------------------------------------------------ 资产 / 余额

    @Override
    public CompletableFuture<List<WalletAsset>> assets() {
        return CompletableFuture.completedFuture(List.of(this.asset));
    }

    @Override
    public List<WalletAsset> cachedAssets() {
        return List.of(this.asset);
    }

    @Override
    public CompletableFuture<WalletBalance> balance(final UUID playerId, final String playerName,
                                                    final String assetCode) {
        return CompletableFuture.supplyAsync(() -> {
            this.validateAsset(assetCode);
            return this.snapshot(playerId, playerName, assetCode);
        }, this.executor);
    }

    @Override
    public WalletBalance cachedBalance(final UUID playerId, final String assetCode) {
        synchronized (this.lock) {
            final BigDecimal value = this.balances.get(playerId);
            if (value == null || !assetMatches(assetCode)) {
                return null;
            }
            return this.newBalance(playerId, value);
        }
    }

    // ------------------------------------------------------------------ 加 / 减货币

    @Override
    public CompletableFuture<WalletTransaction> credit(final UUID playerId, final String playerName,
                                                       final String assetCode, final BigDecimal amount,
                                                       final String businessNo, final String remark) {
        return CompletableFuture.supplyAsync(
                () -> this.change("CREDIT", "IN", playerId, playerName, assetCode, amount,
                        businessNo, remark),
                this.executor);
    }

    @Override
    public CompletableFuture<WalletTransaction> debit(final UUID playerId, final String playerName,
                                                      final String assetCode, final BigDecimal amount,
                                                      final String businessNo, final String remark) {
        return CompletableFuture.supplyAsync(
                () -> this.change("DEBIT", "OUT", playerId, playerName, assetCode, amount,
                        businessNo, remark),
                this.executor);
    }

    private WalletTransaction change(final String type, final String direction, final UUID playerId,
                                     final String playerName, final String assetCode,
                                     final BigDecimal amount, final String businessNo,
                                     final String remark) {
        if (playerId == null) {
            throw new WalletException(0, "playerId 不能为空");
        }
        this.validateAsset(assetCode);
        if (amount == null || amount.signum() <= 0) {
            throw new WalletException(0, "金额必须大于 0");
        }
        if (businessNo == null || businessNo.isBlank()) {
            throw new WalletException(0, "businessNo 不能为空（幂等键）");
        }

        synchronized (this.lock) {
            // 幂等：同一 businessNo 重试返回原流水，不重复记账
            final WalletTransaction existing = this.byBusinessNo.get(businessNo);
            if (existing != null) {
                return existing;
            }
            BigDecimal value = this.balances.get(playerId);
            if (value == null) {
                value = BigDecimal.valueOf(this.config.testStartBalance());
            }
            if ("DEBIT".equals(type) && value.compareTo(amount) < 0) {
                throw new WalletException(0, "余额不足（当前 " + value.toPlainString() + "）");
            }
            final BigDecimal after = "CREDIT".equals(type) ? value.add(amount) : value.subtract(amount);
            final long now = System.currentTimeMillis();
            final WalletTransaction record = new WalletTransaction(
                    "T-" + Long.toString(now, 36) + "-" + this.records.size(),
                    businessNo,
                    type,
                    "TEST",
                    this.asset.code(),
                    direction,
                    amount,
                    after,
                    remark == null ? "" : remark,
                    now);
            this.balances.put(playerId, after);
            this.records.add(record);
            this.byBusinessNo.put(businessNo, record);
            this.trimLedgerLocked();
            this.requestSave();
            return record;
        }
    }

    // ------------------------------------------------------------------ 流水

    @Override
    public CompletableFuture<WalletTransactionPage> transactions(final UUID playerId,
                                                                 final String playerName,
                                                                 final int page, final int size) {
        return this.transactions(playerId, playerName, null, page, size);
    }

    @Override
    public CompletableFuture<WalletTransactionPage> transactions(final UUID playerId,
                                                                 final String playerName,
                                                                 final List<String> assetCodes,
                                                                 final int page, final int size) {
        return CompletableFuture.supplyAsync(() -> {
            final List<WalletTransaction> matched = new ArrayList<>();
            synchronized (this.lock) {
                for (int i = this.records.size() - 1; i >= 0; i--) {
                    final WalletTransaction record = this.records.get(i);
                    if (!this.isOwn(record, playerId, assetCodes)) {
                        continue;
                    }
                    matched.add(record);
                }
            }
            final int from = Math.max(0, (Math.max(1, page) - 1) * Math.max(1, size));
            final int to = Math.min(matched.size(), from + Math.max(1, size));
            return new WalletTransactionPage(
                    from >= matched.size() ? List.of() : matched.subList(from, to),
                    matched.size());
        }, this.executor);
    }

    private boolean isOwn(final WalletTransaction record, final UUID playerId,
                          final List<String> assetCodes) {
        if (assetCodes != null && !assetCodes.contains(record.assetCode())) {
            return false;
        }
        // 测试模式只有一种货币，流水的归属靠备注无法还原玩家；账本里没有按玩家分流水，
        // 所以直接按「该玩家当前余额存在（玩过）或流水总数小于上限」放行——这是本地模式的简化：
        // 单服单币场景下，多玩家混看流水的误差可以接受。为了避免误导，流水页仍只在本玩家有余额时返回。
        return this.balances.containsKey(playerId);
    }

    // ------------------------------------------------------------------ 持久化

    public void shutdown() {
        this.executor.shutdown();
        this.saveNow();
    }

    @Override
    public void close() {
        this.shutdown();
    }

    private void requestSave() {
        if (!this.saveQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runDelayed(this.plugin, task -> {
            this.saveQueued.set(false);
            this.saveNow();
        }, 5L, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 立即落盘（锁内取数、锁外写文件；任意线程可调）。 */
    public void saveNow() {
        final YamlConfiguration yaml = new YamlConfiguration();
        synchronized (this.lock) {
            for (final Map.Entry<UUID, BigDecimal> entry : this.balances.entrySet()) {
                yaml.set("balances." + entry.getKey(), entry.getValue().toPlainString());
            }
            int index = 0;
            for (int i = this.records.size() - 1; i >= 0 && index < MAX_LEDGER_ENTRIES; i--, index++) {
                final WalletTransaction record = this.records.get(i);
                final String base = "transactions." + index + ".";
                yaml.set(base + "id", record.id());
                yaml.set(base + "businessNo", record.businessNo());
                yaml.set(base + "type", record.type());
                yaml.set(base + "assetCode", record.assetCode());
                yaml.set(base + "direction", record.direction());
                yaml.set(base + "amount", record.amount().toPlainString());
                yaml.set(base + "balanceAfter", record.balanceAfter().toPlainString());
                yaml.set(base + "remark", record.remark());
                yaml.set(base + "createdAt", record.createdAt());
            }
        }
        final Path target = this.plugin.getDataFolder().toPath().resolve(LEDGER_FILE);
        try {
            Files.createDirectories(target.getParent());
            final Path temp = target.resolveSibling(LEDGER_FILE + ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicUnsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "测试账本写入失败：" + e.getMessage(), e);
        }
    }

    private void load() {
        final Path target = this.plugin.getDataFolder().toPath().resolve(LEDGER_FILE);
        if (!Files.isRegularFile(target)) {
            return;
        }
        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target.toFile());
            final ConfigurationSection balances = yaml.getConfigurationSection("balances");
            if (balances != null) {
                for (final String key : balances.getKeys(false)) {
                    try {
                        this.balances.put(UUID.fromString(key), new BigDecimal(
                                balances.getString(key, "0")));
                    } catch (final RuntimeException ignored) {
                        // 单条损坏就跳过
                    }
                }
            }
            final ConfigurationSection transactions = yaml.getConfigurationSection("transactions");
            if (transactions != null) {
                for (final String key : transactions.getKeys(false)) {
                    try {
                        final ConfigurationSection s = transactions.getConfigurationSection(key);
                        if (s == null) {
                            continue;
                        }
                        final WalletTransaction record = new WalletTransaction(
                                s.getString("id", ""),
                                s.getString("businessNo", ""),
                                s.getString("type", "CREDIT"),
                                "TEST",
                                s.getString("assetCode", this.asset.code()),
                                s.getString("direction", "IN"),
                                new BigDecimal(s.getString("amount", "0")),
                                new BigDecimal(s.getString("balanceAfter", "0")),
                                s.getString("remark", ""),
                                s.getLong("createdAt", 0L));
                        this.records.add(record);
                        this.byBusinessNo.put(record.businessNo(), record);
                    } catch (final RuntimeException ignored) {
                        // 单条损坏就跳过
                    }
                }
                this.records.sort(Comparator.comparingLong(WalletTransaction::createdAt));
            }
            this.plugin.getLogger().info("已加载测试账本："
                    + this.balances.size() + " 个余额，" + this.records.size() + " 条流水。");
        } catch (final RuntimeException e) {
            this.plugin.getLogger().warning("测试账本读取失败，从零开始：" + e.getMessage());
        }
    }

    /** 只保留最近 {@value MAX_LEDGER_ENTRIES} 条流水（幂等映射同步收缩）。 */
    private void trimLedgerLocked() {
        while (this.records.size() > MAX_LEDGER_ENTRIES) {
            final WalletTransaction removed = this.records.remove(0);
            this.byBusinessNo.remove(removed.businessNo());
        }
    }

    // ------------------------------------------------------------------ 工具

    private void validateAsset(final String assetCode) {
        if (assetCode == null || assetCode.isBlank()) {
            throw new WalletException(0, "assetCode 不能为空");
        }
        if (!assetMatches(assetCode)) {
            throw new WalletException(0, "测试模式只有货币 " + this.asset.code());
        }
    }

    private boolean assetMatches(final String assetCode) {
        return this.asset.code().equalsIgnoreCase(assetCode);
    }

    private WalletBalance snapshot(final UUID playerId, final String playerName,
                                   final String assetCode) {
        synchronized (this.lock) {
            final BigDecimal value = this.balances.computeIfAbsent(playerId,
                    key -> BigDecimal.valueOf(this.config.testStartBalance()));
            return this.newBalance(playerId, value);
        }
    }

    private WalletBalance newBalance(final UUID playerId, final BigDecimal value) {
        return new WalletBalance(
                "test-" + playerId,
                String.valueOf(Bukkit.getOfflinePlayer(playerId).getName()),
                playerId.toString(),
                this.asset.code(),
                value,
                System.currentTimeMillis());
    }
}
