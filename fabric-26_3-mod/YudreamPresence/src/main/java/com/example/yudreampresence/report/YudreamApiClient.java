package com.example.yudreampresence.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 直连 YuDream Admin 的 {@code minecraft-server} 插件上报端点。
 *
 * <p>路径与 Bukkit 桥接插件一致：
 * {@code {base-url}/api/plugins/minecraft-server/servers/{server-id}/players/...}，
 * 请求头 {@code X-API-Key} 需要 {@code plugin:minecraft-server:report} 权限。
 */
public final class YudreamApiClient implements ReportTransport {

    private static final String USER_AGENT = "YudreamPresence/0.1.0";

    private final String baseUrl;
    private final String serverId;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public YudreamApiClient(String baseUrl, String serverId, String apiKey,
                            int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl;
        this.serverId = serverId;
        this.apiKey = apiKey;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public HttpResult post(String relativePath, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(relativePath)).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("X-API-Key", apiKey);

            byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }

            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return new HttpResult(status, body);
        } finally {
            connection.disconnect();
        }
    }

    String url(String relativePath) {
        return baseUrl + "/api/plugins/minecraft-server/servers/"
                + encodePathSegment(serverId) + "/" + relativePath;
    }

    static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(line);
            }
        } finally {
            reader.close();
        }
        return body.toString();
    }
}
