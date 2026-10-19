package com.example.yudreamplaytime;

/**
 * 时长显示格式：{@code hh小时mm分钟}。
 *
 * <p>不足 1 小时只显示分钟（{@code 35分钟}），没有记录时为 {@code 0分钟}；
 * 有小时时分钟补零（{@code 1小时05分钟}）。与 yudream-minecraft-server 的
 * {@code /yudreammc playtime} 输出保持一致，方便两边对照。
 */
public final class DurationFormat {

    private DurationFormat() {
    }

    public static String format(final long millis) {
        final long totalMinutes = Math.max(millis, 0L) / 60000L;
        final long hours = totalMinutes / 60L;
        final long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            return minutes + "分钟";
        }

        return hours + "小时" + (minutes < 10L ? "0" + minutes : String.valueOf(minutes)) + "分钟";
    }
}
