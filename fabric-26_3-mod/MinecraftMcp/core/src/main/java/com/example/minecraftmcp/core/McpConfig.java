package com.example.minecraftmcp.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

public record McpConfig(boolean enabled, int port, String token, int requestTimeoutMs,
                        boolean allowCommands, boolean allowChat) {
    public McpConfig {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port 必须为 0..65535（0 为随机端口）");
        if (token == null || token.length() < 32 || !token.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("token 至少 32 位，仅允许字母、数字、下划线和连字符");
        if (requestTimeoutMs < 1000 || requestTimeoutMs > 60000)
            throw new IllegalArgumentException("requestTimeoutMs 必须为 1000..60000");
    }

    public static McpConfig load(Path path) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(path)) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            McpConfig initial = new McpConfig(true, 8765,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), 15000, true, true);
            Files.createDirectories(path.getParent());
            try {
                Files.createFile(path, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException ignored) {
                Files.createFile(path);
            }
            Files.writeString(path, gson.toJson(initial) + "\n", StandardCharsets.UTF_8);
            return initial;
        }
        JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return new McpConfig(bool(object, "enabled", true), integer(object, "port", 8765),
                object.has("token") ? object.get("token").getAsString() : "",
                integer(object, "requestTimeoutMs", 15000), bool(object, "allowCommands", true),
                bool(object, "allowChat", true));
    }

    private static boolean bool(JsonObject o, String k, boolean fallback) {
        return o.has(k) ? o.get(k).getAsBoolean() : fallback;
    }
    private static int integer(JsonObject o, String k, int fallback) {
        return o.has(k) ? o.get(k).getAsBigDecimal().intValueExact() : fallback;
    }
}
