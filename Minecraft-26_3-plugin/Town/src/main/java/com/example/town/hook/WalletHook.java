package com.example.town.hook;

import com.example.town.TownPlugin;
import com.example.wallet.api.WalletApi;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/**
 * Wallet 软依赖隔离层：所有 com.example.wallet 类型只能出现在本类里（见 exp/paper-plugin.md 软依赖类加载）。
 * 只有 {@link #create(Plugin)} 返回非 null 后，本类才会被加载。
 */
public final class WalletHook implements EconomyHook {

    /** 扣费结果。 */
    public record Pay(boolean ok, String symbol) {
        public static Pay ok(String symbol) {
            return new Pay(true, symbol);
        }

        public static Pay fail() {
            return new Pay(false, "");
        }
    }

    private final WalletService service;

    private WalletHook(WalletService service) {
        this.service = service;
    }

    public String id() { return "wallet"; }
    public String name() { return "Wallet"; }

    /** 外部入口：Wallet 未装 / 未就绪时返回 null（不抛错）。 */
    public static WalletHook create(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Wallet") == null) {
            return null;
        }
        try {
            WalletService service = WalletApi.walletOrNull();
            if (service == null) {
                plugin.getLogger().warning("检测到 Wallet 但服务未就绪，经济功能暂不可用。");
                return null;
            }
            plugin.getLogger().info("已接入 Wallet 经济服务。");
            return new WalletHook(service);
        } catch (LinkageError e) {
            plugin.getLogger().warning("Wallet API 类不可用，经济功能禁用：" + e.getClass().getSimpleName());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 资产
    // ------------------------------------------------------------------

    /** 配置币种优先；未配置时取缓存资产里第一个启用的。找不到返回空串（调用方再报错）。 */
    public String resolveCurrency(String configured) {
        if (!configured.isBlank()) {
            return configured;
        }
        List<WalletAsset> assets = service.cachedAssets();
        for (WalletAsset asset : assets) {
            if (asset.enabled()) {
                return asset.code();
            }
        }
        return "";
    }

    /** 按配置币种 / 自动币种取符号（用于消息展示），取不到回退币种码本身。 */
    public String symbolOf(String configured, String code) {
        for (WalletAsset asset : service.cachedAssets()) {
            if (asset.code().equals(code)) {
                String symbol = asset.symbol();
                return symbol == null || symbol.isBlank() ? asset.name() : symbol;
            }
        }
        return code.isEmpty() ? configured : code;
    }

    /** 预热资产缓存；失败也正常完成，避免预热失败中断启动或重载。 */
    public CompletableFuture<Void> preloadAssets() {
        try {
            return service.assets()
                    .thenAccept(assets -> { })
                    .exceptionally(error -> null);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** 余额（缓存优先，未命中走网络；供确认界面显示）。 */
    public CompletableFuture<Double> balance(UUID player, String name, String code) {
        return service.balance(player, name, code)
                .thenApply(b -> b.balance().setScale(2, RoundingMode.DOWN).doubleValue())
                .exceptionally(e -> -1.0);
    }

    public Double cachedBalance(UUID player, String code) {
        var balance = service.cachedBalance(player, code);
        return balance == null ? null : balance.balance().doubleValue();
    }

    /** 精确金额版本供持久化支付协调器调用；保留异常，不能把超时当成未扣款。 */
    public CompletableFuture<Void> debitCents(com.example.town.economy.Payment p) {
        return service.debit(p.payer(),p.payerName(),p.currency(),BigDecimal.valueOf(p.cents(),2),
                p.debitKey(),"Town "+p.kind()).thenAccept(ignored->{});
    }
    public CompletableFuture<Void> creditCents(com.example.town.economy.Payment p,boolean refund) {
        return service.credit(refund?p.payer():p.beneficiary(),refund?p.payerName():p.beneficiaryName(),p.currency(),
                BigDecimal.valueOf(refund?p.cents():p.creditCents(),2),refund?p.refundKey():p.creditKey(),
                refund?"Town 操作退款":"Town 离线结算").thenAccept(ignored->{});
    }
    public boolean definiteRejection(Throwable error) {
        if(!(error instanceof com.example.wallet.api.WalletException e)) return false;
        int code=e.statusCode();
        return code==400 || code==402 || code==422 || (code==0 && e.getMessage()!=null && e.getMessage().startsWith("余额不足"));
    }

    // ------------------------------------------------------------------
    // 扣费 / 入账
    // ------------------------------------------------------------------

    /**
     * 扣费。businessNo 传确定性幂等键，remark 记业务明细便于官网对账。
     * 任何异常（含余额不足、网络失败）都归一为 Pay.fail()。
     */
    public CompletableFuture<Pay> debit(UUID player, String name, String code,
                                        double amount, String businessNo, String remark) {
        return service.debit(player, name, code,
                        BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP),
                        businessNo, remark)
                .thenApply(t -> Pay.ok(symbolOf("", code)))
                .exceptionally(e -> Pay.fail());
    }

    /** 退款 / 发奖（镇长收到镇政厅补贴等扩展用）。 */
    public CompletableFuture<Pay> credit(UUID player, String name, String code,
                                         double amount, String businessNo, String remark) {
        return service.credit(player, name, code,
                        BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP),
                        businessNo, remark)
                .thenApply(t -> Pay.ok(symbolOf("", code)))
                .exceptionally(e -> Pay.fail());
    }
}
