package com.example.mainmenu.util;

/**
 * 时长格式化：毫秒 → “X 天 X 小时 X 分钟”这类可读文本。
 */
public final class Durations {

    private Durations() {
    }

    public static String format(final long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        final long days = seconds / 86_400L;
        seconds %= 86_400L;
        final long hours = seconds / 3_600L;
        seconds %= 3_600L;
        final long minutes = seconds / 60L;
        final long secs = seconds % 60L;

        if (days > 0) {
            return days + " 天 " + hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        if (minutes > 0) {
            return minutes + " 分钟 " + secs + " 秒";
        }
        return secs + " 秒";
    }
}
