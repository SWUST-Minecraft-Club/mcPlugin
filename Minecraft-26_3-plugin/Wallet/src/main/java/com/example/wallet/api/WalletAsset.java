package com.example.wallet.api;

import java.math.BigDecimal;

/** 当前后端的一种货币（资产）。{@code enabled} 是该后端的全局启用状态。 */
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
