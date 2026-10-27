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
import org.bukkit.ChunkSnapshot;

/**
 * 原版地图风格的小地图渲染器。真实 key 只有在 PNG 写盘且本次 CE 最终资源包验证成功后才会标记为 ready，
 * 防止“文件存在但资源包里没有”导致 image 空白。所有回调仅表达结果，ArcMenu META 由 TownUi 在玩家线程写入。
 */
public final class TownMapRenderer {

    public static final int CHUNKS = 7;
    private static final int SCALE = 2;
    private static final int MAX_MAPS = 512;
    private static final long REBUILD_THROTTLE_MS = 5_000L;
    private static final long BATCH_DELAY_MS = 1_000L;
    private static final String RENDER_VERSION = "v3";

    private final TownPlugin plugin;
    private final Path imageDir;
    private final Path packFile;
    private final Set<String> inFlight = new HashSet<>();
    private final Set<String> pendingIndex = new HashSet<>();
    private final Set<String> indexed = new HashSet<>();
    private final Map<String, List<CompletableFuture<Boolean>>> waiters = new HashMap<>();
    private final Map<ChunkKey, CompletableFuture<int[]>> tiles = new java.util.LinkedHashMap<>();
    private boolean rebuildRunning;
    private boolean rebuildScheduled;
    private boolean rebuildAgain;
    private long lastRebuild;

    /** 已有最终包校验通过即可复用，开服和配置重载不会为了同一张地图再次上传。 */
    public void initialize() {
        Threads.async(plugin,()->{
            ensurePlaceholder();
            try {
                Path merged=plugin.resourcePacks().currentPack();
                if(restoreCachedPack(merged)) {
                    plugin.resourcePacks().published(merged);
                    plugin.getLogger().info("已复用小镇地图资源缓存（"+indexed.size()+" 张），无需重新合包上传。");
                }
            } catch(Exception error) {
                plugin.getLogger().fine("地图缓存需重新索引："+error.getMessage());
            }
            Threads.global(plugin,()->{
                warmSpawn();
                requestResourceRebuild();
            });
        });
    }

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
            if(!Files.isRegularFile(fileFor("default")))
                javax.imageio.ImageIO.write(img, "png", fileFor("default").toFile());
            synchronized (this) {
                pendingIndex.add("default");
            }
        } catch (final IOException e) {
            plugin.getLogger().warning("小地图占位图写入失败：" + e.getMessage());
        }
    }

    /** 不是只信磁盘缓存：逐一比较字体映射、源 PNG 和最终包内 CRC。 */
    boolean restoreCachedPack(Path merged) throws IOException {
        if(!Files.isRegularFile(packFile) || !Files.isRegularFile(merged)) return false;
        Set<String> keys=new HashSet<>();
        try(var stream=Files.list(imageDir)) {
            stream.filter(p->p.getFileName().toString().endsWith(".png"))
                    .map(p->p.getFileName().toString().replaceFirst("\\.png$",""))
                    .filter(key->key.equals("default") || key.startsWith(RENDER_VERSION+"_"))
                    .forEach(keys::add);
        }
        if(keys.isEmpty() || !verifyMergedPack(merged,keys,0)) return false;
        try(ZipFile zip=new ZipFile(merged.toFile())) {
            for(String key:keys) {
                var entry=zip.getEntry("assets/arcmenu/textures/images/town/map/"+key+".png");
                var crc=new java.util.zip.CRC32();
                crc.update(Files.readAllBytes(fileFor(key)));
                if(entry==null || crc.getValue()!=entry.getCrc()) return false;
            }
        }
        synchronized(this) { indexed.addAll(keys); pendingIndex.removeAll(keys); }
        return true;
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
                jobs.add(tile(world,new ChunkKey(center.world(),cx,cz)).thenAccept(colors -> {
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            for (int sy = 0; sy < SCALE; sy++) {
                                for (int sx = 0; sx < SCALE; sx++) {
                                    img.setRGB((px + dx) * SCALE + sx, (pz + dz) * SCALE + sy,
                                            colors[dx*16+dz]);
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

    /** 相邻的 7×7 视图共享区块采样；读取世界只在所属区域线程，着色在异步线程。 */
    private synchronized CompletableFuture<int[]> tile(World world,ChunkKey key) {
        var cached=tiles.get(key);
        if(cached!=null) return cached;
        CompletableFuture<int[]> result=new CompletableFuture<>();
        tiles.put(key,result);
        while(tiles.size()>2048) tiles.remove(tiles.keySet().iterator().next());
        world.getChunkAtAsync(key.x(),key.z()).whenComplete((chunk,error)->{
            if(error!=null) {result.completeExceptionally(error);return;}
            Bukkit.getRegionScheduler().run(plugin,world,key.x(),key.z(),task->{
                try {
                    ChunkSnapshot snapshot=chunk.getChunkSnapshot(true,true,false);
                    Threads.async(plugin,()->{
                        try {
                            int[] colors=new int[256];
                            for(int x=0;x<16;x++) for(int z=0;z<16;z++)
                                colors[x*16+z]=columnColor(snapshot,x,z).getRGB();
                            result.complete(colors);
                        } catch(RuntimeException failure) {result.completeExceptionally(failure);}
                    });
                } catch(RuntimeException failure) {result.completeExceptionally(failure);}
            });
        });
        result.whenComplete((ignored,error)->{if(error!=null) synchronized(this) {tiles.remove(key,result);}});
        return result;
    }

    private void markWritten(final String key) {
        synchronized (this) {
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

    /** 短延迟收齐同批请求，5 秒冷却且有尾随重建；不为一张初始占位图单独合包。 */
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
            if(plugin.craftEngine()==null) return;
            delay = Math.max(BATCH_DELAY_MS,lastRebuild + REBUILD_THROTTLE_MS - System.currentTimeMillis());
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
                    if(rebuildRunning) { rebuildAgain=true; return; }
                    if(pendingIndex.isEmpty()) return;
                    rebuildRunning=true;
                }
                doRebuild();
            });
        } else {
            Threads.global(plugin, this::doRebuild);
        }
    }

    /** 在全局区域线程发起本地索引重建；最终合包与投递验证在异步工作流完成。 */
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
        if (ok) {
            // arcmenu resources 只构建本地字体索引，必须继续完成 CE 的合包、上传和发送工作流。
            plugin.resourcePacks().rebuild().whenComplete((pack, error) -> {
                boolean delivered = false;
                if (error == null && pack != null) {
                    try {
                        delivered = verifyMergedPack(pack, batch, startedAt);
                        if (delivered) plugin.resourcePacks().published(pack);
                    } catch (Exception failure) {
                        plugin.getLogger().warning("小镇最终资源包验证失败：" + failure.getMessage());
                    }
                } else if (error != null) {
                    plugin.getLogger().warning("CE 小镇资源包构建失败：" + error.getMessage());
                }
                finishBatch(batch, delivered);
            });
        } else {
            finishBatch(batch, false);
        }
        return ok;
    }

    private void finishBatch(final Set<String> batch, final boolean ok) {
        final boolean again;
        final Map<String, List<CompletableFuture<Boolean>>> done = new HashMap<>();
        synchronized (this) {
            if (ok) {
                indexed.addAll(batch);
            } else {
                pendingIndex.addAll(batch);
            }
            lastRebuild = System.currentTimeMillis()+(ok?0:25_000L);
            rebuildRunning = false;
            for (final String key : batch) {
                inFlight.remove(key);
                final List<CompletableFuture<Boolean>> callbacks = waiters.remove(key);
                if (callbacks != null) {
                    done.put(key, callbacks);
                }
            }
            again = rebuildAgain || !pendingIndex.isEmpty();
            rebuildAgain = false;
        }
        final boolean success = ok;
        done.forEach((key, callbacks) -> complete(callbacks, success));
        if (again) {
            requestResourceRebuild();
        }
        if (!ok) {
            plugin.getLogger().warning("最终资源包未就绪，地图继续显示区块网格（" + batch.size() + " 张）。");
        }
    }

    /** PNG 和字体映射必须同时进入最终包；只有图片而没有字体依然会出现放大的缺字白框。 */
    private boolean verifyMergedPack(Path merged, Set<String> batch, long startedAt) throws IOException {
        if (!Files.exists(merged) || Files.getLastModifiedTime(merged).toMillis() < startedAt - 1000L) return false;
        try (ZipFile arc = new ZipFile(packFile.toFile()); ZipFile ce = new ZipFile(merged.toFile())) {
            final String font = "assets/arcmenu/font/images.json";
            var sourceFont = arc.getEntry(font);
            var mergedFont = ce.getEntry(font);
            if (sourceFont == null || mergedFont == null) return false;
            try (var source = arc.getInputStream(sourceFont); var target = ce.getInputStream(mergedFont)) {
                var expectedProviders = com.google.gson.JsonParser.parseString(new String(source.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
                var actualProviders = com.google.gson.JsonParser.parseString(new String(target.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
                if (expectedProviders == null || actualProviders == null) return false;
                for (var provider : expectedProviders) if (!actualProviders.contains(provider)) return false;
            }
            for (String key : batch) {
                String path = "assets/arcmenu/textures/images/town/map/" + key + ".png";
                var source = arc.getEntry(path);
                var target = ce.getEntry(path);
                if (source == null || target == null || source.getCrc() != target.getCrc()) return false;
            }
            return true;
        }
    }

    public boolean containsCurrentMaps(Path pack) throws IOException {
        final Set<String> keys;
        synchronized (this) { keys = Set.copyOf(indexed); }
        return verifyMergedPack(pack, keys, 0L);
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
                        .filter(path -> {
                            synchronized(this) { return !inFlight.contains(path.getFileName().toString().replaceFirst("\\.png$","")); }
                        })
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

    private Color columnColor(final ChunkSnapshot snapshot, final int x, final int z) {
            final int y = snapshot.getHighestBlockYAt(x,z);
            final Material type = snapshot.getBlockType(x,y,z);
            final String name = type.name();
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
                final Color grass = grassColor(snapshot.getBiome(x,y,z).getKey().getKey().toUpperCase(java.util.Locale.ROOT));
                return shade(new Color(grass.getRed() * 70 / 100,
                        grass.getGreen() * 75 / 100, grass.getBlue() * 70 / 100), y);
            }
            if (name.contains("GRASS_BLOCK") || name.contains("PODZOL") || name.contains("MYCELIUM")
                    || name.contains("MOSS") || name.contains("DIRT")) {
                return shade(grassColor(snapshot.getBiome(x,y,z).getKey().getKey().toUpperCase(java.util.Locale.ROOT)), y);
            }
            return shade(new Color(0x7D, 0x7D, 0x7D), y);
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
