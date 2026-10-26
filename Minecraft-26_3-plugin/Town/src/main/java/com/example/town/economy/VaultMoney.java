package com.example.town.economy;

import java.math.BigDecimal;

/** Vault 使用 double；拒绝不支持的精度与不能无损表达的金额。 */
public final class VaultMoney {
    private VaultMoney() { }

    public static double amount(long cents, int digits) {
        if (cents < 0 || (digits == 0 && cents % 100 != 0) || (digits == 1 && cents % 10 != 0)) {
            throw new VaultLedger.Rejected("Vault 经济插件不支持这笔金额的精度");
        }
        BigDecimal exact = BigDecimal.valueOf(cents, 2);
        double amount = exact.doubleValue();
        if (!matches(amount, cents)) throw new VaultLedger.Rejected("金额超出 Vault 的精确表示范围");
        return amount;
    }

    public static boolean matches(double amount, long cents) {
        return Double.isFinite(amount) && BigDecimal.valueOf(amount).compareTo(BigDecimal.valueOf(cents, 2)) == 0;
    }
}
