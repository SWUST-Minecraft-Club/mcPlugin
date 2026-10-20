package com.example.wallet.api;

import java.math.BigDecimal;

/** 某个玩家在某种货币上的余额（{@code userId} 是官网的站内用户标识）。 */
public record WalletBalance(
        String userId,
        String playerName,
        String playerUuid,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {
}
