package com.example.wallet.internal;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Explicit currency routes; an unavailable website never changes the source of local funds. */
public final class RoutedWalletService implements WalletService {
    private final WalletConfig config;
    private final WalletService local, website;
    private volatile List<WalletAsset> assets;

    public RoutedWalletService(WalletConfig config, WalletService local, WalletService website) {
        this.config = config; this.local = local; this.website = website;
        this.assets = config.currencies();
    }

    private WalletAsset asset(String code) {
        return assets.stream().filter(a -> a.code().equalsIgnoreCase(code) && a.enabled()).findFirst()
                .orElseThrow(() -> new WalletException(0, "币种不存在或未启用：" + code));
    }
    private boolean remote(String code) { return config.source(code).equals("website"); }
    private String routedCode(String code) { return remote(code) ? config.routes().get(code).websiteCode() : code; }
    private WalletService service(String code) { return remote(code) ? website : local; }

    @Override public List<WalletAsset> cachedAssets() { return assets; }
    @Override public CompletableFuture<List<WalletAsset>> assets() {
        if (config.routes().values().stream().noneMatch(r -> r.source().equals("website"))) {
            return CompletableFuture.completedFuture(assets);
        }
        return website.assets().thenApply(site -> {
            final List<WalletAsset> merged = new ArrayList<>();
            for (WalletAsset declared : config.currencies()) {
                if (!remote(declared.code())) { merged.add(declared); continue; }
                final WalletAsset found = site.stream().filter(a -> a.code().equalsIgnoreCase(routedCode(declared.code())))
                        .findFirst().orElse(null);
                merged.add(new WalletAsset(declared.code(), declared.name(), declared.symbol(), declared.scale(),
                        declared.money(), declared.enabled() && found != null && found.enabled() && found.scale() == declared.scale(),
                        declared.transferEnabled() && found != null && found.transferEnabled()));
            }
            return assets = List.copyOf(merged);
        });
    }

    private CompletableFuture<WalletAsset> verified(String code) {
        final WalletAsset selected = asset(code);
        if (!remote(selected.code())) return CompletableFuture.completedFuture(selected);
        return website.assets().thenApply(site -> {
            final WalletAsset found = site.stream().filter(a -> a.code().equalsIgnoreCase(routedCode(selected.code())))
                    .findFirst().orElseThrow(() -> new WalletException(0, "官网不存在币种：" + routedCode(selected.code())));
            if (!found.enabled() || found.scale() != selected.scale()) {
                throw new WalletException(0, "官网币种已停用或配置精度不一致：" + selected.code());
            }
            return selected;
        });
    }

    @Override public CompletableFuture<WalletBalance> balance(UUID id, String name, String code) {
        try {
            return verified(code).thenCompose(a -> service(a.code()).balance(id, name, routedCode(a.code()))
                    .thenApply(b -> remap(b, a.code())));
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }
    @Override public WalletBalance cachedBalance(UUID id, String code) {
        final WalletAsset selected = asset(code);
        final WalletBalance value = service(selected.code()).cachedBalance(id, routedCode(selected.code()));
        return value == null ? null : remap(value, selected.code());
    }
    private WalletBalance remap(WalletBalance value, String code) {
        if (!value.assetCode().equalsIgnoreCase(routedCode(code))) throw new WalletException(0, "官网返回币种与配置不一致：" + code);
        return new WalletBalance(value.userId(), value.playerName(), value.playerUuid(), code, value.balance(), value.updatedAt());
    }
    private WalletTransaction remap(WalletTransaction value, String code) {
        if (!value.assetCode().equalsIgnoreCase(routedCode(code))) throw new WalletException(0, "官网返回流水币种与配置不一致：" + code);
        return new WalletTransaction(value.id(), value.businessNo(), value.type(), value.source(), code,
                value.direction(), value.amount(), value.balanceAfter(), value.remark(), value.createdAt());
    }
    @Override public CompletableFuture<WalletTransaction> credit(UUID id, String name, String code, BigDecimal amount, String business, String remark) {
        return change(id, name, code, amount, business, remark, "add");
    }
    @Override public CompletableFuture<WalletTransaction> debit(UUID id, String name, String code, BigDecimal amount, String business, String remark) {
        return change(id, name, code, amount, business, remark, "take");
    }
    @Override public CompletableFuture<WalletTransaction> setBalance(UUID id, String name, String code, BigDecimal amount, String business, String remark) {
        return change(id, name, code, amount, business, remark, "set");
    }
    private CompletableFuture<WalletTransaction> change(UUID id, String name, String code, BigDecimal amount, String business, String remark, String action) {
        try {
            final WalletAsset selected = asset(code);
            if (action.equals("set") && remote(selected.code())) {
                return CompletableFuture.failedFuture(new WalletException(0, "set 仅支持本地币种；官网币种请使用 add/take"));
            }
            final BigDecimal value = amount.setScale(selected.scale(), RoundingMode.UNNECESSARY);
            if (value.signum() < 0 || (!action.equals("set") && value.signum() == 0)) throw new WalletException(0, "金额无效");
            return verified(code).thenCompose(a -> {
                final WalletService backend = service(a.code());
                final String target = routedCode(a.code());
                final CompletableFuture<WalletTransaction> result = switch (action) {
                    case "set" -> backend.setBalance(id, name, target, value, business, remark);
                    case "add" -> backend.credit(id, name, target, value, business, remark);
                    default -> backend.debit(id, name, target, value, business, remark);
                };
                return result.thenApply(tx -> remap(tx, a.code()));
            });
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }

    @Override public CompletableFuture<WalletTransactionPage> transactions(UUID id, String name, int page, int size) {
        return transactions(id, name, null, page, size);
    }
    @Override public CompletableFuture<WalletTransactionPage> transactions(UUID id, String name, List<String> codes, int page, int size) {
        try {
            final List<WalletAsset> selected = assets.stream().filter(WalletAsset::enabled)
                    .filter(a -> codes == null || codes.stream().anyMatch(c -> c.equalsIgnoreCase(a.code()))).toList();
            if (selected.isEmpty()) return CompletableFuture.completedFuture(new WalletTransactionPage(List.of(), 0));
            final int safePage = Math.max(1, page), safeSize = Math.clamp(size, 1, 200);
            if (selected.size() == 1) {
                final String code = selected.getFirst().code();
                return service(code).transactions(id, name, List.of(routedCode(code)), safePage, safeSize)
                        .thenApply(batch -> new WalletTransactionPage(batch.records().stream().map(tx -> remap(tx, code)).toList(), batch.total()));
            }
            final long need = (long) safePage * safeSize;
            if (need > 10000) throw new WalletException(0, "合并流水最多浏览前 10000 条；请指定币种查询更早流水");
            final List<CompletableFuture<WalletTransactionPage>> parts = selected.stream()
                    .map(a -> collect(id, name, a.code(), (int) need, 1, new ArrayList<>())).toList();
            return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                final List<WalletTransaction> rows = new ArrayList<>();
                long total = 0;
                for (var part : parts) { final var result = part.join(); rows.addAll(result.records()); total += result.total(); }
                rows.sort(Comparator.comparingLong(WalletTransaction::createdAt).reversed()
                        .thenComparing(WalletTransaction::assetCode).thenComparing(WalletTransaction::id));
                final int from = (int) Math.min((long) (safePage - 1) * safeSize, rows.size());
                return new WalletTransactionPage(List.copyOf(rows.subList(from, Math.min(from + safeSize, rows.size()))), total);
            });
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }
    private CompletableFuture<WalletTransactionPage> collect(UUID id, String name, String code, int need, int page, List<WalletTransaction> rows) {
        return service(code).transactions(id, name, List.of(routedCode(code)), page, 200).thenCompose(batch -> {
            rows.addAll(batch.records().stream().map(tx -> remap(tx, code)).toList());
            if (rows.size() >= need || rows.size() >= batch.total() || batch.records().isEmpty()) {
                if (batch.records().isEmpty() && rows.size() < Math.min(need, batch.total())) {
                    throw new WalletException(0, "后端返回了不完整的流水页：" + code);
                }
                return CompletableFuture.completedFuture(new WalletTransactionPage(List.copyOf(rows), batch.total()));
            }
            return collect(id, name, code, need, page + 1, rows);
        });
    }
}
