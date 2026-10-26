package com.example.town.hook;

import com.example.town.economy.Payment;
import com.example.town.economy.VaultLedger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Town 统一经济入口；接口不引用任何可选经济插件的类型。 */
public interface EconomyHook {
    String id();
    String name();
    String resolveCurrency(String configured);
    String symbolOf(String configured, String code);
    CompletableFuture<Void> preloadAssets();
    CompletableFuture<Double> balance(UUID player, String name, String code);
    Double cachedBalance(UUID player, String code);
    CompletableFuture<Void> debitCents(Payment payment);
    CompletableFuture<Void> creditCents(Payment payment, boolean refund);
    boolean definiteRejection(Throwable error);
    default boolean acceptsCurrency(String code) { return !code.isBlank() && !code.startsWith("vault:"); }
    default VaultLedger vaultLedger() { return null; }

    /** 旧台账没有来源标记，仍只允许 Wallet 处理；切换来源不能重放到另一本账。 */
    default void checkBinding(Payment payment) {
        if (!payment.payload().getOrDefault("_economy", "wallet").equals(id())) {
            throw new IllegalStateException("交易 " + payment.id() + " 属于其他经济来源，请恢复原来源后结算");
        }
    }
}
