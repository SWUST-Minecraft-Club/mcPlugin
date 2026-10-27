package com.example.vehicles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

final class VehicleResources {
    private VehicleResources() {}

    static void install(Vehicles plugin) throws IOException {
        Path folder = plugin.getDataFolder().toPath();
        Files.createDirectories(folder);
        Path standalone = folder.resolve("Vehicles-mc26_3-resourcepack-" + plugin.getPluginMeta().getVersion() + ".zip");
        if (!Files.exists(standalone)) {
            try (InputStream stream = plugin.getResource("vehicles-resourcepack.zip")) {
                if (stream == null) throw new IOException("JAR 中缺少 vehicles-resourcepack.zip");
                Files.copy(stream, standalone);
            }
        }
        plugin.getLogger().info("原版资源包已释放到 plugins/Vehicles/" + standalone.getFileName());
        if (!plugin.settings().installToCraftEngine()
                || plugin.getServer().getPluginManager().getPlugin("CraftEngine") == null) return;
        Path root = folder.getParent().resolve("CraftEngine/resources/vehicles");
        Files.createDirectories(root);
        Path manifest = root.resolve("pack.yml");
        if (!Files.exists(manifest)) Files.writeString(manifest, "name: Vehicles\nnamespace: vehicles\nversion: '"
                + plugin.getPluginMeta().getVersion() + "'\nauthor: zhou-BZDDX\ndescription: 小镇漫游车模型与纹理\n");
        int installed = 0;
        try (InputStream resource = plugin.getResource("vehicles-resourcepack.zip")) {
            if (resource == null) throw new IOException("资源包不存在");
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().startsWith("assets/vehicles/")) continue;
                    Path path = root.resolve("resourcepack").resolve(entry.getName()).normalize();
                    if (!path.startsWith(root.normalize())) throw new IOException("资源包路径越界");
                    if (Files.exists(path)) continue;
                    Files.createDirectories(path.getParent());
                    Files.copy(zip, path); installed++;
                }
            }
        }
        if (installed > 0) plugin.getLogger().info("已安装 " + installed + " 个 CraftEngine 载具资产；执行 /ce reload all 合包并发送给玩家。");
    }
}
