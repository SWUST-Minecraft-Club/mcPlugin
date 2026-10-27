package com.example.handbookcore.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.plugin.java.JavaPlugin;

/** 只释放缺少的文件；保留运营者对 CE 包的修改。 */
public final class ResourceInstaller {
    private ResourceInstaller() { }
    public static int install(JavaPlugin plugin) throws IOException {
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().getParent().resolve("CraftEngine/resources");
        // CE 首次启动会自行释放 internal；不要提前建立 resources/ 导致其跳过初始化。
        if (!Files.isDirectory(root.resolve("internal"))) return 0;
        int installed = 0;
        try (InputStream stream = plugin.getResource("ce-pack-files.txt")) {
            if (stream == null) throw new IOException("缺少 CE 资源清单");
            for (String resource : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (resource.isBlank()) continue;
                Path destination = root.resolve(resource.substring("ce-pack/".length())).normalize();
                if (!destination.startsWith(root)) throw new IOException("无效资源路径");
                if (Files.exists(destination)) continue;
                Files.createDirectories(destination.getParent());
                try (InputStream data = plugin.getResource(resource)) {
                    if (data == null) throw new IOException("资源不存在：" + resource);
                    Files.copy(data, destination); installed++;
                }
            }
        }
        return installed;
    }
}
