package com.example.wallet.api;

import java.math.BigDecimal;

/** 某个玩家在某种货币上的余额（官网模式的 userId 为站内标识；本地模式为 local-UUID）。 */
public record WalletBalance(
        String userId,
        String playerName,
        String playerUuid,
        String assetCode,
        BigDecimal balance,
        long updatedAt
) {
}
