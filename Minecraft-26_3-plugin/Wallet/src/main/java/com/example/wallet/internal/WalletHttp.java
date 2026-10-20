package com.example.wallet.internal;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.WalletException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 官网钱包接口的极简 HTTP 客户端。
 *
 * <p>三条与官网一致的约定：
 * <ul>
 *     <li>响应被平台包了一层 {@code {"code":..,"message":..,"data":..}}，真正的内容在 {@code data}；</li>
 *     <li>长整型/BigDecimal 可能被序列化成**字符串**，解析时两种都认；</li>
 *     <li>鉴权用 {@code X-API-Key}（与 yudream 桥接插件一致）。</li>
 * </ul>
 */
public final class WalletHttp {

    private final WalletConfig config;
    private final String endpoint;

    public WalletHttp(final WalletConfig config) {
        this.config = config;
        this.endpoint = config.baseUrl() + "/api/plugins/yudream-wallet";
    }

    public JsonObject get(final String path, final Map<String, String> query) {
        return execute("GET", path, query, null);
    }

    public JsonObject post(final String path, final JsonObject body) {
        return execute("POST", path, null, body == null ? null : body.toString());
    }

    private JsonObject execute(final String method, final String path, final Map<String, String> query,
                               final String body) {
        final StringBuilder url = new StringBuilder(this.endpoint).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (final Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                url.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                first = false;
            }
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url.toString()).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(this.config.timeoutMs());
            connection.setReadTimeout(this.config.timeoutMs());
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-API-Key", this.config.apiKey());
            connection.setRequestProperty("User-Agent", "YudreamWalletBukkit/1.0");
            if (body != null) {
                final byte[] data = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                }
            }

            final int status = connection.getResponseCode();
            final JsonObject root = parseObject(read(status >= 400
                    ? connection.getErrorStream() : connection.getInputStream()));
            final String message = str(root, "message", "");
            if (status >= 400) {
                throw new WalletException(status, message.isEmpty() ? ("HTTP " + status) : message);
            }

            final int code = intOf(root, "code", 0);
            if (code != 0 && code != 200) {
                throw new WalletException(code, message.isEmpty() ? ("业务错误 " + code) : message);
            }

            final JsonObject payload = object(root, "data");
            return payload != null ? payload : (root != null ? root : new JsonObject());
        } catch (IOException e) {
            throw new WalletException(0, "请求官网失败：" + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String encode(final String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (IOException e) {
            return "";
        }
    }

    private static String read(final InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        final StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(line);
            }
        }
        return body.toString();
    }

    // ------------------------------------------------------------------ JSON 解析（数字/字符串都认）

    static JsonObject parseObject(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            final JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static JsonObject object(final JsonObject parent, final String key) {
        if (parent == null) {
            return null;
        }
        final JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonArray array(final JsonObject parent, final String key) {
        if (parent == null) {
            return null;
        }
        final JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static String str(final JsonObject parent, final String key, final String fallback) {
        if (parent == null) {
            return fallback;
        }
        final JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public static long lng(final JsonObject parent, final String key, final long fallback) {
        final BigDecimal value = decimal(parent, key);
        return value == null ? fallback : value.longValue();
    }

    public static int intOf(final JsonObject parent, final String key, final int fallback) {
        final BigDecimal value = decimal(parent, key);
        return value == null ? fallback : value.intValue();
    }

    public static boolean bool(final JsonObject parent, final String key, final boolean fallback) {
        if (parent == null) {
            return fallback;
        }
        final JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            final var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                return Boolean.parseBoolean(primitive.getAsString());
            }
        }
        return fallback;
    }

    public static BigDecimal decimal(final JsonObject parent, final String key) {
        if (parent == null) {
            return null;
        }
        final JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            final var primitive = element.getAsJsonPrimitive();
            try {
                return new BigDecimal(primitive.getAsString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
