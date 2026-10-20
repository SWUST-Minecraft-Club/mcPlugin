package com.example.wallet.util;

import com.example.wallet.api.WalletAsset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 显示格式：金额（按货币的 scale）、时间（配置的时区）、时长（hh小时mm分钟）。 */
public final class Format {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Format() {
    }

    public static String amount(final BigDecimal value, final WalletAsset asset) {
        final BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        final int scale = asset == null ? 0 : Math.max(asset.scale(), 0);
        final String text = safe.setScale(scale, RoundingMode.DOWN).toPlainString();
        final String symbol = asset == null ? "" : asset.symbol();
        return symbol == null || symbol.isBlank() ? text : text + " " + symbol;
    }

    public static String time(final long epochMillis, final ZoneId zone) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return TIME.withZone(zone == null ? ZoneId.systemDefault() : zone)
                .format(Instant.ofEpochMilli(epochMillis));
    }

    /** 与 YudreamPlaytime / /yudreammc playtime 一致的时长格式。 */
    public static String duration(final long millis) {
        final long totalMinutes = Math.max(millis, 0L) / 60000L;
        final long hours = totalMinutes / 60L;
        final long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            return minutes + "分钟";
        }
        return hours + "小时" + (minutes < 10L ? "0" + minutes : String.valueOf(minutes)) + "分钟";
    }
}
