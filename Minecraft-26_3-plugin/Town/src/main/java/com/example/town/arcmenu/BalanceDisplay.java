package com.example.town.arcmenu;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 仅用于余额呈现；缩写不会参与结算，完整值保留在悬停信息中。 */
final class BalanceDisplay {
    private BalanceDisplay() { }

    static String full(final BigDecimal value) {
        final String[] parts = value.stripTrailingZeros().toPlainString().split("\\.", 2);
        final String integer = parts[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
        return integer + (parts.length == 2 ? "." + parts[1] : "");
    }

    static String amount(final BigDecimal value) {
        final String full = full(value);
        if (MenuBuilder.inkWidth(full, 15) <= 170) {
            return full;
        }
        final BigDecimal magnitude = value.abs();
        final int exponent = magnitude.precision() - magnitude.scale() - 1;
        if (exponent >= 8 && exponent < 12) {
            return compact(value, 8, "亿");
        }
        if (exponent >= 12 && exponent < 16) {
            return compact(value, 12, "万亿");
        }
        if (exponent >= 16) {
            return compact(value, exponent, "e+" + exponent);
        }
        // 极小值或高精度小数同样不能撑开金额栏；仅缩写有效数字。
        return "≈" + value.round(new java.math.MathContext(7, RoundingMode.DOWN)).stripTrailingZeros();
    }

    private static String compact(final BigDecimal value, final int power, final String suffix) {
        return "≈" + value.movePointLeft(power).setScale(2, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString() + suffix;
    }

    static String plain(final String value) {
        return MenuBuilder.stripCodes(TownMenus.miniToAmp(value)).replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
    }

    static String currency(final String value) {
        final String plain = plain(value);
        if (MenuBuilder.inkWidth(plain, 10) <= 72) {
            return plain;
        }
        final StringBuilder result = new StringBuilder();
        for (final int codepoint : plain.codePoints().toArray()) {
            final String next = result.toString() + new String(Character.toChars(codepoint));
            if (MenuBuilder.inkWidth(next + "…", 10) > 72) {
                break;
            }
            result.appendCodePoint(codepoint);
        }
        return result + "…";
    }
}
