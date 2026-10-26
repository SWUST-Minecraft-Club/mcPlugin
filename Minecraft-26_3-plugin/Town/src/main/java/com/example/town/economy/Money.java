package com.example.town.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() { }
    public static long cents(String value) {
        long result = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        if (result < 0) throw new IllegalArgumentException("金额不能为负");
        return result;
    }
    public static long cents(double value) { return cents(BigDecimal.valueOf(value).toPlainString()); }
    public static BigDecimal decimal(long cents) { return BigDecimal.valueOf(cents, 2); }
    public static String format(long cents) { return decimal(cents).stripTrailingZeros().toPlainString(); }
}
