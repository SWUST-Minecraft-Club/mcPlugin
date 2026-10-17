package com.example.mainmenu.playtime;

import com.example.mainmenu.MainMenuPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 读取 YuDream 在线时长的 HTTP 客户端。
 *
 * <p>数据来源与规范（见 docs/功能插件接入规范.md 第 6 节）：
 * YuDream 宿主插件 {@code minecraft-server} 维护 {@code player-activities} 文档，
 * 跨进程读取走宿主插件 HTTP 分发器：
 * <pre>
 * GET {base-url}/api/plugins/minecraft-server/admin/servers/{serverId}/players?page=0&size=200
 * 权限：plugin:minecraft-server:manage
 * 鉴权：X-API-Key: yda_xxx   或   Authorization: Bearer yda_xxx  或   Authorization: {Sa-Token}
 * </pre>
 * 响应体（可能被宿主包一层 code/data，这里做了兼容）：
 * <pre>
 * {"records":[{"serverId","playerId","playerName","online","afk",
 *              "totalOnlineMillis","totalAfkMillis","currentOnlineSince","currentAfkSince",...}],
 *  "total": 123}
 * </pre>
 */
public final class YuDreamPlaytimeClient {

    private final MainMenuPlugin plugin;
    private final HttpClient http;

    public YuDreamPlaytimeClient(final MainMenuPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, timeoutMillis())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 是否已配置（未配置时菜单显示“未配置后端”而不是一直转圈） */
    public boolean isConfigured() {
        return !baseUrl().isBlank() && !serverId().isBlank() && (!apiKey().isBlank() || !token().isBlank());
    }

    /**
     * 查询某个玩家的在线时长。会按页拉取直到找到该玩家或翻完所有页。
     *
     * @return 找到时返回数据；后端没有该玩家记录时返回空
     */
    public Optional<PlaytimeInfo> fetch(final String playerName, final UUID playerId)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            return Optional.empty();
        }
        final int pageSize = Math.min(200, Math.max(1, plugin.getConfig().getInt("yudream.page-size", 200)));
        final int maxPages = Math.max(1, plugin.getConfig().getInt("yudream.max-pages", 10));

        for (int page = 0; page < maxPages; page++) {
            final JsonObject payload = request(page, pageSize);
            if (payload == null) {
                return Optional.empty();
            }
            final JsonArray records = payload.has("records") && payload.get("records").isJsonArray()
                    ? payload.getAsJsonArray("records")
                    : new JsonArray();
            for (final JsonElement element : records) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject record = element.getAsJsonObject();
                if (matches(record, playerName, playerId)) {
                    return Optional.of(toInfo(record));
                }
            }
            final int total = payload.has("total") && payload.get("total").isJsonPrimitive()
                    ? payload.get("total").getAsInt()
                    : records.size();
            if ((page + 1) * pageSize >= total || records.isEmpty()) {
                break;
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------

    private JsonObject request(final int page, final int size) throws IOException, InterruptedException {
        final String url = baseUrl() + "/api/plugins/minecraft-server/admin/servers/"
                + serverId() + "/players?page=" + page + "&size=" + size;

        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(1000L, timeoutMillis())))
                .header("Accept", "application/json")
                .GET();
        if (!apiKey().isBlank()) {
            builder.header("X-API-Key", apiKey());
        } else {
            builder.header("Authorization", token());
        }

        final HttpResponse<String> response =
                http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            plugin.getLogger().warning("读取在线时长失败：HTTP " + response.statusCode()
                    + "（" + url + "）");
            return null;
        }
        return unwrap(JsonParser.parseString(response.body()));
    }

    /** 兼容 {records:..} / {data:{records:..}} / {code:..,data:{data:{records:..}}} 这几种包裹方式 */
    private static JsonObject unwrap(final JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject current = root.getAsJsonObject();
        for (int depth = 0; depth < 4; depth++) {
            if (current.has("records")) {
                return current;
            }
            JsonObject next = null;
            for (final String key : List.of("data", "result", "payload", "body")) {
                final JsonElement candidate = current.get(key);
                if (candidate != null && candidate.isJsonObject()) {
                    next = candidate.getAsJsonObject();
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return null;
    }

    private static boolean matches(final JsonObject record, final String playerName, final UUID playerId) {
        final String id = asString(record, "playerId");
        final String name = asString(record, "playerName");
        if (name != null && playerName != null && name.equalsIgnoreCase(playerName)) {
            return true;
        }
        if (id == null || playerId == null) {
            return false;
        }
        return id.equalsIgnoreCase(playerId.toString())
                || id.replace("-", "").equalsIgnoreCase(playerId.toString().replace("-", ""));
    }

    private static PlaytimeInfo toInfo(final JsonObject record) {
        return new PlaytimeInfo(
                asLong(record, "totalOnlineMillis"),
                asLong(record, "totalAfkMillis"),
                asBoolean(record, "online"),
                asNullableLong(record, "currentOnlineSince"),
                System.currentTimeMillis()
        );
    }

    private static String asString(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static long asLong(final JsonObject object, final String key) {
        final Long value = asNullableLong(object, key);
        return value == null ? 0L : value;
    }

    private static Long asNullableLong(final JsonObject object, final String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean asBoolean(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    // ------------------------------------------------------------------

    private String baseUrl() {
        return plugin.getConfig().getString("yudream.base-url", "").trim().replaceAll("/+$", "");
    }

    private String apiKey() {
        return plugin.getConfig().getString("yudream.api-key", "").trim();
    }

    private String token() {
        return plugin.getConfig().getString("yudream.token", "").trim();
    }

    /** 服务器 ID：配置优先，其次读环境变量 MCSERVER_ID（mcpanel 注入） */
    private String serverId() {
        final String configured = plugin.getConfig().getString("yudream.server-id", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        final String fromEnv = System.getenv("MCSERVER_ID");
        return fromEnv == null ? "" : fromEnv.trim();
    }

    private long timeoutMillis() {
        return plugin.getConfig().getLong("yudream.timeout-ms", 5000L);
    }
}
