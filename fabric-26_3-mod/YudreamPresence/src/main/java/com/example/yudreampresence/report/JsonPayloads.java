package com.example.yudreampresence.report;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 上报请求体。
 *
 * <p>字段与 Bukkit 桥接插件保持一致：事件只带 {@code playerId / playerName / eventAt}，
 * 快照带 {@code observedAt + players}。后端按 {@code playerId}（UUID）对账。
 */
public final class JsonPayloads {

    private JsonPayloads() {
    }

    public static String event(UUID playerId, String playerName, long eventAt) {
        return "{\"playerId\":\"" + escape(playerId.toString())
                + "\",\"playerName\":\"" + escape(playerName)
                + "\",\"eventAt\":" + eventAt + '}';
    }

    public static String snapshot(Collection<PlayerSnapshot> players, long observedAt) {
        StringBuilder json = new StringBuilder(64).append("{\"observedAt\":").append(observedAt).append(",\"players\":[");
        boolean first = true;
        for (PlayerSnapshot player : List.copyOf(players)) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"playerId\":\"").append(escape(player.playerId().toString()))
                    .append("\",\"playerName\":\"").append(escape(player.playerName())).append("\"}");
        }
        return json.append("]}").toString();
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\b' -> result.append("\\b");
                case '\t' -> result.append("\\t");
                case '\n' -> result.append("\\n");
                case '\f' -> result.append("\\f");
                case '\r' -> result.append("\\r");
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (c >= ' ') {
                        result.append(c);
                    } else {
                        result.append(String.format("\\u%04x", (int) c));
                    }
                }
            }
        }
        return result.toString();
    }
}
