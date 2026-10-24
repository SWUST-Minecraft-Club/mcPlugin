package com.example.town.arcmenu;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.util.Threads;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 原版地图风格的小地图渲染器：每个区块按 16×16 逐列采样（地表材质 + 生物群系草色 + 水深 + 高度明暗），
 * 画成 288×288 PNG 放入 ArcMenu images 目录，由菜单的动态 image 元素显示。
 * 新图写入后用控制台身份触发 ArcMenu 资源重建（节流），并预渲染邻域中心减少重建次数。
 */
public final class TownMapRenderer {

    /** 7×7 区块：格子在 16:9 菜单里能到 44 设计单位，标签才看得清。 */
    public static final int CHUNKS = 7;
    private static final int SCALE = 2;
    private static final long REBUILD_THROTTLE_MS = 30_000L;

    private final TownPlugin plugin;
    private final Path imageDir;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private volatile long lastRebuild;

    public TownMapRenderer(final TownPlugin plugin, final Path arcMenuDir) {
        this.plugin = plugin;
        this.imageDir = arcMenuDir.resolve("images").resolve("town").resolve("map");
    }

    /** world_x_z 形式的图片键（ArcMenu 图片路径只允许小写字母数字下划线连字符点）。 */
    public static String fileKey(final ChunkKey center) {
        return center.world().toLowerCase(java.util.Locale.ROOT)
                + "_" + center.x() + "_" + center.z();
    }

    public Path fileFor(final String key) {
        return imageDir.resolve(key + ".png");
    }

    /** 启动 / 重载时保证占位图存在（未生成的区域显示这张）。 */
    public void ensurePlaceholder() {
        try {
            Files.createDirectories(imageDir);
            final Path file = fileFor("default");
            if (Files.exists(file)) {
                return;
            }
            final BufferedImage img = new BufferedImage(
                    CHUNKS * 16 * SCALE, CHUNKS * 16 * SCALE, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x18, 0x29, 0x23));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(new Color(0x2B, 0x3C, 0x35));
            for (int i = 0; i <= CHUNKS * 16; i += 16) {
                g.fillRect(i * SCALE, 0, SCALE, img.getHeight());
                g.fillRect(0, i * SCALE, img.getWidth(), SCALE);
            }
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", file.toFile());
        } catch (final IOException e) {
            plugin.getLogger().warning("小地图占位图写入失败：" + e.getMessage());
        }
    }

    /** PNG 已在磁盘则直接回调；否则异步渲染后触发资源重建再回调。 */
    public void ensureRendered(final ChunkKey center, final Runnable onReady) {
        final String key = fileKey(center);
        if (Files.exists(fileFor(key)) || !inFlight.add(key)) {
            onReady.run();
            return;
        }
        final World world = Bukkit.getWorld(center.world());
        if (world == null) {
            inFlight.remove(key);
            onReady.run();
            return;
        }
        final BufferedImage img = new BufferedImage(
                CHUNKS * 16 * SCALE, CHUNKS * 16 * SCALE, BufferedImage.TYPE_INT_RGB);
        final List<CompletableFuture<Void>> jobs = new ArrayList<>();
        final int half = CHUNKS / 2;
        for (int r = 0; r < CHUNKS; r++) {
            for (int c = 0; c < CHUNKS; c++) {
                final int cx = center.x() + c - half;
                final int cz = center.z() + r - half;
                final int px = c * 16;
                final int pz = r * 16;
                jobs.add(world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            final Color color = columnColor(world, (cx << 4) + dx, (cz << 4) + dz);
                            for (int sy = 0; sy < SCALE; sy++) {
                                for (int sx = 0; sx < SCALE; sx++) {
                                    img.setRGB((px + dx) * SCALE + sx, (pz + dz) * SCALE + sy,
                                            color.getRGB());
                                }
                            }
                        }
                    }
                }));
            }
        }
        CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).thenRun(() -> {
            Threads.async(plugin, () -> {
                try {
                    Files.createDirectories(imageDir);
                    javax.imageio.ImageIO.write(img, "png", fileFor(key).toFile());
                } catch (final IOException e) {
                    plugin.getLogger().warning("小地图 " + key + " 写入失败：" + e.getMessage());
                } finally {
                    inFlight.remove(key);
                }
                requestResourceRebuild();
                // 等资源包重建完成再刷新界面，否则玩家拿到的还是旧索引
                Threads.globalLater(plugin, 40, onReady);
            });
        });
    }

    /** 控制台身份重建 ArcMenu 资源包；30 秒内只触发一次。 */

    /**
     * 图片文件集合有变化时返回 true（并写回签名），调用方据此触发一次资源重建。
     * ArcMenu 的 menus().reload() 只发布菜单、不重建图片索引，新 PNG 必须走 resources 才会进包。
     */
    public boolean imagesChangedSinceLastIndex() {
        try {
            Files.createDirectories(imageDir);
            final StringBuilder sig = new StringBuilder();
            try (var stream = Files.list(imageDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".png"))
                        .sorted()
                        .forEach(p -> {
                            try {
                                sig.append(p.getFileName()).append(':').append(Files.size(p)).append(';');
                            } catch (final IOException ignored) {
                            }
                        });
            }
            final String current = sig.toString();
            final Path marker = imageDir.resolve("..").resolve("map-index.marker").normalize();
            if (Files.exists(marker) && current.equals(Files.readString(marker).trim())) {
                return false;
            }
            Files.writeString(marker, current);
            return true;
        } catch (final IOException e) {
            plugin.getLogger().warning("小地图索引签名失败：" + e.getMessage());
            return true;
        }
    }

    /** 启动预热：为世界出生点渲染一张地图，玩家常去区域打开即秒显示。 */
    public void warmSpawn() {
        final org.bukkit.World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return;
        }
        final ChunkKey center = ChunkKey.of(world.getSpawnLocation());
        if (!Files.exists(fileFor(fileKey(center)))) {
            ensureRendered(center, () -> {
            });
        }
    }

    /** 控制台身份重建 ArcMenu 资源包；30 秒内只触发一次。 */
    public void requestResourceRebuild() {
        final long now = System.currentTimeMillis();
        if (now - lastRebuild < REBUILD_THROTTLE_MS) {
            return;
        }
        lastRebuild = now;
        Threads.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "arcmenu resources"));
    }

    /** 单列颜色：地表类别 + 生物群系草色 + 水深 + 高度明暗，风格对齐原版地图。 */
    private Color columnColor(final World world, final int x, final int z) {
        try {
            final Block top = world.getHighestBlockAt(x, z);
            final Material type = top.getType();
            final String name = type.name();
            final int y = top.getY();
            if (name.contains("WATER")) {
                return shade(waterColor(y), y);
            }
            if (name.contains("LAVA")) {
                return new Color(0xE0, 0x78, 0x30);
            }
            if (name.contains("ICE") || name.contains("SNOW")) {
                return shade(new Color(0xF2, 0xF8, 0xFB), y);
            }
            if (name.contains("SAND") || name.contains("GRAVEL")) {
                return shade(new Color(0xDB, 0xD3, 0xA0), y);
            }
            if (name.contains("LEAVES") || name.contains("LOG") || name.contains("WOOD")
                    || name.contains("CACTUS")) {
                final Color grass = grassColor(top.getBiome().name());
                return shade(new Color(
                        grass.getRed() * 70 / 100,
                        grass.getGreen() * 75 / 100,
                        grass.getBlue() * 70 / 100), y);
            }
            if (name.contains("GRASS_BLOCK") || name.contains("PODZOL") || name.contains("MYCELIUM")
                    || name.contains("MOSS") || name.contains("DIRT")) {
                return shade(grassColor(top.getBiome().name()), y);
            }
            return shade(new Color(0x7D, 0x7D, 0x7D), y);
        } catch (final RuntimeException e) {
            return new Color(0x1A, 0x1A, 0x1A);
        }
    }

    /** 常见生物群系的草色近似（原版地图以群系着色）。 */
    private static Color grassColor(final String biome) {
        if (biome.contains("DESERT") || biome.contains("BEACH")) {
            return new Color(0xBF, 0xB7, 0x55);
        }
        if (biome.contains("JUNGLE")) {
            return new Color(0x59, 0xC9, 0x3C);
        }
        if (biome.contains("DARK_FOREST")) {
            return new Color(0x50, 0x7A, 0x32);
        }
        if (biome.contains("BIRCH")) {
            return new Color(0x88, 0xBB, 0x67);
        }
        if (biome.contains("TAIGA") || biome.contains("SNOWY") || biome.contains("GROVE")) {
            return new Color(0x86, 0xB7, 0x83);
        }
        if (biome.contains("SWAMP")) {
            return new Color(0x6A, 0x70, 0x39);
        }
        if (biome.contains("SAVANNA") || biome.contains("BADLANDS") || biome.contains("MESA")) {
            return new Color(0xBF, 0xB7, 0x55);
        }
        if (biome.contains("FOREST") || biome.contains("CHERRY") || biome.contains("MEADOW")) {
            return new Color(0x79, 0xC0, 0x5A);
        }
        return new Color(0x91, 0xBD, 0x59);
    }

    /** 水深渐变：越深越暗，贴近原版地图的海洋分层。 */
    private static Color waterColor(final int surfaceY) {
        if (surfaceY >= 62) {
            return new Color(0x43, 0x7B, 0xE0);
        }
        if (surfaceY >= 50) {
            return new Color(0x35, 0x5F, 0xC9);
        }
        if (surfaceY >= 38) {
            return new Color(0x2A, 0x4C, 0xAE);
        }
        return new Color(0x1F, 0x3B, 0x99);
    }

    /** 高度明暗：高原亮、低谷暗，制造原版地图的起伏感。 */
    private static Color shade(final Color base, final int y) {
        final double factor;
        if (y >= 100) {
            factor = 1.18;
        } else if (y >= 75) {
            factor = 1.06;
        } else if (y >= 58) {
            factor = 1.0;
        } else if (y >= 45) {
            factor = 0.88;
        } else {
            factor = 0.76;
        }
        return new Color(
                Math.min(255, (int) (base.getRed() * factor)),
                Math.min(255, (int) (base.getGreen() * factor)),
                Math.min(255, (int) (base.getBlue() * factor)));
    }
}
