package com.example.yudreampresence;

import com.example.yudreampresence.config.PresenceConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 测试用配置构造：直接写一份 properties 再交给正式加载器。 */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static PresenceConfig write(Path directory, String... lines) throws IOException {
        Path file = directory.resolve("yudream-presence.properties");
        String content = String.join("\n", lines) + "\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return PresenceConfig.load(file);
    }

    public static PresenceConfig configured(Path directory) throws IOException {
        return write(directory,
                "base-url=http://127.0.0.1:8080",
                "server-id=test-server",
                "api-key=test-key");
    }
}
