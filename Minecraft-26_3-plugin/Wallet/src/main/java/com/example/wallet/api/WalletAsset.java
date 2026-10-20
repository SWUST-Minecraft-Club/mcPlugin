package com.example.wallet.api;

import java.math.BigDecimal;

/** 官网钱包里的一种货币（资产）。{@code enabled} 是官网的全局启用状态。 */
public record WalletAsset(
        String code,
        String name,
        String symbol,
        int scale,
        boolean money,
        boolean enabled,
        boolean transferEnabled
) {
}
