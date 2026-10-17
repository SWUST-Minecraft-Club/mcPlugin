package com.example.mainmenu.playtime;

/**
 * 一次在线时长查询结果。
 *
 * @param totalOnlineMillis  累计在线（含当前会话，由后端在出接口时补上）
 * @param totalAfkMillis     累计挂机
 * @param online             当前是否在线
 * @param currentOnlineSince 本次会话开始时间（epoch 毫秒）；离线为 null
 * @param fetchedAt          本地取到这份数据的时刻
 */
public record PlaytimeInfo(
        long totalOnlineMillis,
        long totalAfkMillis,
        boolean online,
        Long currentOnlineSince,
        long fetchedAt
) {

    /** 本次会话已在线时长（离线时为 0） */
    public long currentSessionMillis(final long now) {
        if (!online || currentOnlineSince == null) {
            return 0L;
        }
        return Math.max(0L, now - currentOnlineSince);
    }
}
