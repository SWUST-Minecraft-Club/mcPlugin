package com.example.yudreampresence.report;

/** 与 YuDream Admin {@code /players/*} 上报端点一一对应的四类状态事件。 */
public enum PlayerEventType {

    JOIN("players/join"),
    QUIT("players/quit"),
    AFK_START("players/afk/start"),
    AFK_END("players/afk/end");

    private final String remotePath;

    PlayerEventType(String remotePath) {
        this.remotePath = remotePath;
    }

    public String remotePath() {
        return remotePath;
    }
}
