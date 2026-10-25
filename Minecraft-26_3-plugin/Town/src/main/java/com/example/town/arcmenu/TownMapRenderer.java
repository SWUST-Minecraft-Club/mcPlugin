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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 原版地图风格的小地图渲染器。真实 key 只有在 PNG 写盘且本次资源包重建验证成功后才会标记为 ready，
 * 防止“文件存在但资源包里没有”导致 image 空白。所有回调仅表达结果，ArcMenu META 由 TownUi 在玩家线程写入。
 */
public final class TownMapRenderer {

    public static final int CHUNKS = 7;
    private static final int SCALE = 2;
    private static final int MAX_MAPS = 512;
    private static final long REBUILD_THROTTLE_MS = 30_000L;
    private static final String RENDER_VERSION = "v3";

    private final TownPlugin plugin;
    private final Path imageDir;
    private final Path packFile;
    private final Set<String> inFlight = new HashSet<>();
    private final Set<String> pendingIndex = new HashSet<>();
    private final Set<String> indexed = new HashSet<>();
    private final Map<String, List<CompletableFuture<Boolean>>> waiters = new HashMap<>();
    private boolean rebuildRunning;
    private boolean rebuildScheduled;
    private boolean rebuildAgain;
    private long lastRebuild;

    public TownMapRenderer(final TownPlugin plugin, final Path arcMenuDir) {
        this.plugin = plugin;
        this.imageDir = arcMenuDir.resolve("images").resolve("town").resolve("map");
        this.packFile = arcMenuDir.resolve("generated").resolve("arcmenu-resourcepack.zip");
    }

    /** 只生成合法的小写路径；版本前缀让旧渲染算法的缓存自然失效。 */
    public static String fileKey(final ChunkKey center) {
        String world = Normalizer.normalize(center.world(), Normalizer.Form.NFKD)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (world.isBlank()) {
            world = "world";
        }
        return RENDER_VERSION + "_" + world + "_" + center.x() + "_" + center.z();
    }

    public Path fileFor(final String key) {
        return imageDir.resolve(key + ".png");
    }

    /** 只有本次资源包已经包含该 key 时才允许菜单切换过去。 */
    public synchronized boolean isReady(final String key) {
        return indexed.contains(key) && Files.exists(fileFor(key));
    }

    public synchronized boolean hasPendingIndex() {
        return !pendingIndex.isEmpty();
    }

    /** 启动时写入浅灰占位图，并让它参与首次资源索引。 */
    public void ensurePlaceholder() {
        try {
            Files.createDirectories(imageDir);
            pruneOldMaps();
            final BufferedImage img = new BufferedImage(
                    CHUNKS * 16 * SCALE, CHUNKS * 16 * SCALE, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            g.setColor(new Color(0xE3, 0xE3, 0xE3));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(new Color(0x8A, 0x8A, 0x8A));
            for (int i = 0; i <= CHUNKS * 16; i += 16) {
                g.fillRect(i * SCALE, 0, SCALE, img.getHeight());
                g.fillRect(0, i * SCALE, img.getWidth(), SCALE);
            }
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", fileFor("default").toFile());
            synchronized (this) {
                pendingIndex.add("default");
            }
        } catch (final IOException e) {
            plugin.getLogger().warning("小地图占位图写入失败：" + e.getMessage());
        }
    }

    /** 启动预热出生点；返回的 Future 只有在资源包验证成功后才完成 true。 */
    public void warmSpawn() {
        final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world != null) {
            ensureRendered(ChunkKey.of(world.getSpawnLocation()));
        }
    }

    /**
     * 同 key 共享等待者；已经 ready 立即完成，已写盘但未入包只补资源索引，不重复渲染。
     * 失败返回 false，调用方继续显示 default，不会因提前切换而白屏。
     */
    public CompletableFuture<Boolean> ensureRendered(final ChunkKey center) {
        final String key = fileKey(center);
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        synchronized (this) {
            if (isReady(key)) {
                result.complete(true);
                return result;
            }
            waiters.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
            if (inFlight.add(key)) {
                startRender(center, key);
            }
        }
        return result;
    }

    private void startRender(final ChunkKey center, final String key) {
        if (Files.exists(fileFor(key))) {
            markWritten(key);
            return;
        }
        final World world = Bukkit.getWorld(center.world());
        if (world == null) {
            fail(key);
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
        CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).thenRun(() ->
                Threads.async(plugin, () -> {
                    try {
                        Files.createDirectories(imageDir);
                        javax.imageio.ImageIO.write(img, "png", fileFor(key).toFile());
                        pruneOldMaps();
                        markWritten(key);
                    } catch (final IOException | RuntimeException e) {
                        plugin.getLogger().warning("小地图 " + key + " 写入失败：" + e.getMessage());
                        fail(key);
                    }
                })).exceptionally(error -> {
                    fail(key);
                    return null;
                });
    }

    private void markWritten(final String key) {
        synchronized (this) {
            inFlight.remove(key);
            pendingIndex.add(key);
        }
        requestResourceRebuild();
    }

    private void fail(final String key) {
        final List<CompletableFuture<Boolean>> failed;
        synchronized (this) {
            inFlight.remove(key);
            failed = waiters.remove(key);
        }
        complete(failed, false);
    }

    /** 30 秒节流有尾随重建：运行中收到新图一定会再安排一次，不会丢批次。 */
    public void requestResourceRebuild() {
        final long delay;
        synchronized (this) {
            if (rebuildRunning) {
                rebuildAgain = true;
                return;
            }
            if (pendingIndex.isEmpty() || rebuildScheduled) {
                return;
            }
            delay = lastRebuild + REBUILD_THROTTLE_MS - System.currentTimeMillis();
            if (delay > 0) {
                rebuildScheduled = true;
            } else {
                rebuildRunning = true;
            }
        }
        if (delay > 0) {
            Threads.globalLater(plugin, Math.max(1L, delay / 50L), () -> {
                synchronized (this) {
                    rebuildScheduled = false;
                }
                requestResourceRebuild();
            });
        } else {
            Threads.global(plugin, this::doRebuild);
        }
    }

    /** 启动/重载时在全局区域线程同步执行，完成后再由调用方发布菜单。 */
    public boolean rebuildNow() {
        final Set<String> batch;
        synchronized (this) {
            if (rebuildRunning) {
                return false;
            }
            if (pendingIndex.isEmpty()) {
                return true;
            }
            rebuildRunning = true;
            batch = takeBatch();
        }
        return executeBatch(batch);
    }

    private Set<String> takeBatch() {
        final Set<String> batch = new HashSet<>(pendingIndex);
        pendingIndex.removeAll(batch);
        return batch;
    }

    private void doRebuild() {
        final Set<String> batch;
        synchronized (this) {
            batch = takeBatch();
        }
        executeBatch(batch);
    }

    private boolean executeBatch(final Set<String> batch) {
        if (batch.isEmpty()) {
            synchronized (this) {
                rebuildRunning = false;
            }
            return true;
        }
        final long startedAt = System.currentTimeMillis();
        boolean ok = false;
        try {
            final boolean commandOk = Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), "arcmenu resources");
            ok = commandOk && verifyPack(batch, startedAt);
        } catch (final RuntimeException e) {
            plugin.getLogger().warning("ArcMenu 资源包重建失败：" + e.getMessage());
        }
        final boolean again;
        final Map<String, List<CompletableFuture<Boolean>>> done = new HashMap<>();
        synchronized (this) {
            if (ok) {
                indexed.addAll(batch);
            } else {
                pendingIndex.addAll(batch);
            }
            lastRebuild = System.currentTimeMillis();
            rebuildRunning = false;
            for (final String key : batch) {
                final List<CompletableFuture<Boolean>> callbacks = waiters.remove(key);
                if (callbacks != null) {
                    done.put(key, callbacks);
                }
            }
            again = rebuildAgain;
            rebuildAgain = false;
        }
        final boolean success = ok;
        done.forEach((key, callbacks) -> complete(callbacks, success));
        if (again) {
            requestResourceRebuild();
        }
        if (!ok) {
            plugin.getLogger().warning("ArcMenu 资源包未包含本批小地图，继续使用占位图（" + batch.size() + " 张）。");
        }
        return ok;
    }

    /** 必须看到本次生成包且包内真的有每个 key，不能把固定延迟当成成功。 */
    private boolean verifyPack(final Set<String> batch, final long startedAt) {
        try {
            if (!Files.exists(packFile)
                    || Files.getLastModifiedTime(packFile).toMillis() < startedAt - 1000L) {
                return false;
            }
            try (ZipFile zip = new ZipFile(packFile.toFile())) {
                for (final String key : batch) {
                    final String suffix = "town/map/" + key + ".png";
                    final boolean found = zip.stream().anyMatch(entry -> entry.getName().endsWith(suffix));
                    if (!found) {
                        return false;
                    }
                }
            }
            return true;
        } catch (final IOException | RuntimeException e) {
            return false;
        }
    }

    private static void complete(final List<CompletableFuture<Boolean>> callbacks, final boolean value) {
        if (callbacks != null) {
            callbacks.forEach(callback -> callback.complete(value));
        }
    }

    /** 只清理本插件生成的旧地图，限制 ArcMenu 图片数量与资源包体积。 */
    private void pruneOldMaps() {
        try {
            final List<Path> maps = new ArrayList<>();
            try (var stream = Files.list(imageDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .filter(path -> !path.getFileName().toString().equals("default.png"))
                        .forEach(maps::add);
            }
            maps.sort((a, b) -> {
                try {
                    return Long.compare(Files.getLastModifiedTime(a).toMillis(),
                            Files.getLastModifiedTime(b).toMillis());
                } catch (final IOException e) {
                    return 0;
                }
            });
            while (maps.size() > MAX_MAPS) {
                final Path old = maps.remove(0);
                final String key = old.getFileName().toString().replaceFirst("\\.png$", "");
                Files.deleteIfExists(old);
                synchronized (this) {
                    indexed.remove(key);
                    pendingIndex.remove(key);
                }
            }
        } catch (final IOException e) {
            plugin.getLogger().warning("小地图缓存清理失败：" + e.getMessage());
        }
    }

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
                return shade(new Color(grass.getRed() * 70 / 100,
                        grass.getGreen() * 75 / 100, grass.getBlue() * 70 / 100), y);
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
