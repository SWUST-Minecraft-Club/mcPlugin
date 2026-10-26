package com.example.wallet.api;

import java.math.BigDecimal;

/**
 * 当前后端的一条钱包流水。
 *
 * @param type       {@code CREDIT} / {@code DEBIT} / {@code TRANSFER}
 * @param direction  {@code IN} / {@code OUT} / {@code TRANSFER}（可能为空）
 * @param balanceAfter 本条流水之后该玩家的余额（转账时取与玩家相关的一侧）
 */
public record WalletTransaction(
        String id,
        String businessNo,
        String type,
        String source,
        String assetCode,
        String direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String remark,
        long createdAt
) {
}
