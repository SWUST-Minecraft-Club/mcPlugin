package com.example.wallet.internal;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletBalance;
import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletService;
import com.example.wallet.api.WalletTransaction;
import com.example.wallet.api.WalletTransactionPage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.plugin.Plugin;

/**
 * 钱包服务实现：所有数据与写操作都走 YuDream 官网，本地只做短时缓存（供菜单每秒重绘使用）。
 */
public final class WalletServiceImpl implements WalletService {

    /** 官网流水单页条数（按币种分别取数时用）。 */
    private static final int SITE_PAGE_SIZE = 100;
    private static final int MAX_SITE_PAGES = 50;

    private final Plugin plugin;
    private final WalletConfig config;
    private final WalletHttp http;
    private final ExecutorService executor;
    private final Map<String, Cached<WalletBalance>> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> userIds = new ConcurrentHashMap<>();
    private volatile Cached<List<WalletAsset>> assetsCache;

    public WalletServiceImpl(final Plugin plugin, final WalletConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.http = new WalletHttp(config);
        this.executor = Executors.newFixedThreadPool(3, runnable -> {
            final Thread thread = new Thread(runnable, "Wallet-Http");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    // ------------------------------------------------------------------ 资产 / 余额

    @Override
    public CompletableFuture<List<WalletAsset>> assets() {
        return supply(this::fetchAssets);
    }

    @Override
    public List<WalletAsset> cachedAssets() {
        final Cached<List<WalletAsset>> cached = this.assetsCache;
        return cached == null ? List.of() : cached.value();
    }

    @Override
    public CompletableFuture<WalletBalance> balance(final UUID playerId, final String playerName,
                                                    final String assetCode) {
        return supply(() -> fetchBalance(playerId, playerName, assetCode));
    }

    @Override
    public WalletBalance cachedBalance(final UUID playerId, final String assetCode) {
        final Cached<WalletBalance> cached = this.balances.get(key(playerId, assetCode));
        return cached == null ? null : cached.value();
    }

    // ------------------------------------------------------------------ 加 / 减货币（唯一写路径：官网）

    @Override
    public CompletableFuture<WalletTransaction> credit(final UUID playerId, final String playerName,
                                                       final String assetCode, final BigDecimal amount,
                                                       final String businessNo, final String remark) {
        return change("/admin/game/credit", playerId, playerName, assetCode, amount, businessNo, remark);
    }

    @Override
    public CompletableFuture<WalletTransaction> debit(final UUID playerId, final String playerName,
                                                      final String assetCode, final BigDecimal amount,
                                                      final String businessNo, final String remark) {
        return change("/admin/game/debit", playerId, playerName, assetCode, amount, businessNo, remark);
    }

    private CompletableFuture<WalletTransaction> change(final String path, final UUID playerId,
                                                        final String playerName, final String assetCode,
                                                        final BigDecimal amount, final String businessNo,
                                                        final String remark) {
        return supply(() -> {
            if (playerId == null) {
                throw new WalletException(0, "playerId 不能为空");
            }
            if (assetCode == null || assetCode.isBlank()) {
                throw new WalletException(0, "assetCode 不能为空");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new WalletException(0, "金额必须大于 0");
            }
            if (businessNo == null || businessNo.isBlank()) {
                throw new WalletException(0, "businessNo 不能为空（幂等键）");
            }

            final JsonObject body = playerBody(playerId, playerName, assetCode);
            body.addProperty("amount", amount);
            body.addProperty("businessNo", businessNo);
            body.addProperty("remark", remark == null ? "" : remark);

            final WalletTransaction transaction = toTransaction(this.http.post(path, body));
            this.balances.remove(key(playerId, assetCode));
            return transaction;
        });
    }

    // ------------------------------------------------------------------ 流水

    @Override
    public CompletableFuture<WalletTransactionPage> transactions(final UUID playerId, final String playerName,
                                                                 final int page, final int size) {
        return transactions(playerId, playerName, null, page, size);
    }

    @Override
    public CompletableFuture<WalletTransactionPage> transactions(final UUID playerId, final String playerName,
                                                                 final List<String> assetCodes,
                                                                 final int page, final int size) {
        return supply(() -> {
            final int safePage = Math.max(page, 1);
            final int safeSize = Math.max(1, Math.min(size, 200));

            // 本服一个币种都没启用：直接空页，不去解析用户
            if (assetCodes != null && assetCodes.isEmpty()) {
                return new WalletTransactionPage(List.of(), 0L);
            }

            final String userId = resolveUserId(playerId, playerName, assetCodes);

            if (assetCodes == null) {
                return fetchTransactions(userId, null, safePage, safeSize);
            }

            if (assetCodes.size() == 1) {
                return fetchTransactions(userId, assetCodes.get(0), safePage, safeSize);
            }

            // 多个币种：每个币种各取够前 safePage*safeSize 条，合并后本地分页（保持"最近优先"）
            final long need = (long) safePage * (long) safeSize;
            final List<WalletTransaction> merged = new ArrayList<>();
            long total = 0L;
            for (final String assetCode : assetCodes) {
                long fetched = 0L;
                long assetTotal = 0L;
                for (int sitePage = 1; sitePage <= MAX_SITE_PAGES; sitePage++) {
                    final WalletTransactionPage batch = fetchTransactions(userId, assetCode, sitePage, SITE_PAGE_SIZE);
                    assetTotal = batch.total();
                    merged.addAll(batch.records());
                    fetched += batch.records().size();
                    if (batch.records().isEmpty() || fetched >= assetTotal || fetched >= need) {
                        break;
                    }
                }
                total += assetTotal;
            }

            merged.sort(Comparator.comparingLong(WalletTransaction::createdAt).reversed());
            final int from = (int)Math.min((long)(safePage - 1) * (long)safeSize, merged.size());
            final int to = (int)Math.min((long)from + (long)safeSize, merged.size());
            return new WalletTransactionPage(List.copyOf(merged.subList(from, to)), total);
        });
    }

    /** 从官网取一页流水；{@code assetCode} 为空表示不限制币种。 */
    private WalletTransactionPage fetchTransactions(final String userId, final String assetCode,
                                                    final int page, final int size) {
        final Map<String, String> query = new LinkedHashMap<>();
        query.put("userId", userId);
        if (assetCode != null && !assetCode.isBlank()) {
            query.put("assetCode", assetCode);
        }
        query.put("page", String.valueOf(page));
        query.put("size", String.valueOf(size));

        final JsonObject payload = this.http.get("/admin/transactions", query);
        final JsonArray records = WalletHttp.array(payload, "records");
        final List<WalletTransaction> list = new ArrayList<>();
        if (records != null) {
            for (final JsonElement element : records) {
                if (element.isJsonObject()) {
                    list.add(toTransaction(element.getAsJsonObject()));
                }
            }
        }
        list.sort(Comparator.comparingLong(WalletTransaction::createdAt).reversed());
        final long total = WalletHttp.lng(payload, "total", list.size());
        return new WalletTransactionPage(List.copyOf(list), total);
    }

    private String resolveUserId(final UUID playerId, final String playerName, final List<String> requested) {
        if (playerId == null) {
            throw new WalletException(0, "playerId 不能为空");
        }

        final String cached = this.userIds.get(playerId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        if (requested != null && !requested.isEmpty()) {
            return fetchBalance(playerId, playerName, requested.getFirst()).userId();
        }

        List<WalletAsset> assets = cachedAssets();
        if (assets.isEmpty()) {
            assets = fetchAssets();
        }

        String assetCode = null;
        for (final WalletAsset asset : assets) {
            if (asset.enabled() && this.config.isLocallyEnabled(asset.code())) {
                assetCode = asset.code();
                break;
            }
        }
        if (assetCode == null) {
            for (final WalletAsset asset : assets) {
                if (asset.enabled()) {
                    assetCode = asset.code();
                    break;
                }
            }
        }
        if (assetCode == null) {
            throw new WalletException(0, "本服暂无可用货币");
        }

        return fetchBalance(playerId, playerName, assetCode).userId();
    }

    // ------------------------------------------------------------------ 内部：拉取与解析

    private List<WalletAsset> fetchAssets() {
        final JsonObject payload = this.http.get("/assets", Map.of("page", "1", "size", "200"));
        final JsonArray records = WalletHttp.array(payload, "records");
        final List<WalletAsset> assets = new ArrayList<>();
        if (records != null) {
            for (final JsonElement element : records) {
                if (element.isJsonObject()) {
                    final JsonObject row = element.getAsJsonObject();
                    assets.add(new WalletAsset(
                            WalletHttp.str(row, "code", ""),
                            WalletHttp.str(row, "name", ""),
                            WalletHttp.str(row, "symbol", ""),
                            WalletHttp.intOf(row, "scale", 0),
                            WalletHttp.bool(row, "money", false),
                            WalletHttp.bool(row, "enabled", false),
                            WalletHttp.bool(row, "transferEnabled", false)
                    ));
                }
            }
        }

        final List<WalletAsset> result = List.copyOf(assets);
        this.assetsCache = new Cached<>(result, System.currentTimeMillis());
        return result;
    }

    private WalletBalance fetchBalance(final UUID playerId, final String playerName, final String assetCode) {
        final JsonObject payload = this.http.post("/admin/game/balance",
                playerBody(playerId, playerName, assetCode));
        final BigDecimal value = WalletHttp.decimal(payload, "balance");
        if (value == null || value.signum() < 0 || WalletHttp.str(payload, "userId", "").isBlank()) {
            throw new WalletException(0, "官网返回了缺失或无效的账户余额");
        }
        final WalletBalance balance = new WalletBalance(
                WalletHttp.str(payload, "userId", ""),
                WalletHttp.str(payload, "playerName", playerName == null ? "" : playerName),
                WalletHttp.str(payload, "playerUuid", playerId == null ? "" : playerId.toString()),
                WalletHttp.str(payload, "assetCode", assetCode),
                value,
                WalletHttp.lng(payload, "updatedAt", System.currentTimeMillis())
        );

        if (playerId != null && !balance.userId().isBlank()) {
            this.userIds.put(playerId, balance.userId());
        }
        this.balances.put(key(playerId, assetCode), new Cached<>(balance, System.currentTimeMillis()));
        return balance;
    }

    private JsonObject playerBody(final UUID playerId, final String playerName, final String assetCode) {
        final JsonObject body = new JsonObject();
        body.addProperty("playerName", playerName == null ? "" : playerName);
        body.addProperty("playerUuid", playerId == null ? "" : playerId.toString());
        body.addProperty("assetCode", assetCode);
        body.addProperty("matchMode", this.config.matchMode());
        return body;
    }

    private static WalletTransaction toTransaction(final JsonObject row) {
        final String type = WalletHttp.str(row, "type", "");
        BigDecimal balanceAfter = WalletHttp.decimal(row, "balanceAfter");
        if (balanceAfter == null) {
            balanceAfter = WalletHttp.decimal(row, "toBalanceAfter");
        }
        if (balanceAfter == null) {
            balanceAfter = WalletHttp.decimal(row, "fromBalanceAfter");
        }

        return new WalletTransaction(
                WalletHttp.str(row, "id", ""),
                WalletHttp.str(row, "businessNo", ""),
                type,
                WalletHttp.str(row, "source", ""),
                WalletHttp.str(row, "assetCode", ""),
                WalletHttp.str(row, "direction", ""),
                amount(WalletHttp.decimal(row, "amount")),
                balanceAfter,
                WalletHttp.str(row, "remark", ""),
                WalletHttp.lng(row, "createdAt", 0L)
        );
    }

    private static BigDecimal amount(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String key(final UUID playerId, final String assetCode) {
        return (playerId == null ? "" : playerId.toString()) + "|" + (assetCode == null ? "" : assetCode);
    }

    private <T> CompletableFuture<T> supply(final java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.executor).whenComplete((value, error) -> {
            if (error != null && !(error.getCause() instanceof WalletException)) {
                this.plugin.getLogger().warning("钱包接口调用失败：" + error.getMessage());
            }
        });
    }

    private record Cached<T>(T value, long at) {
    }
}
