package com.example.wallet.api;

import java.util.List;

/** 一页钱包流水（{@code total} 是总条数，用于算页数）。 */
public record WalletTransactionPage(List<WalletTransaction> records, long total) {
}
