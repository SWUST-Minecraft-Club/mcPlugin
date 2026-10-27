package com.example.handbookcore.hook;

import com.example.handbookcore.api.HandbookItem;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.nio.file.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.*;
import com.example.handbookcore.render.BookScale;

/** CE 的 Adventure 已重定位，通过 JSON 转换组件；主类无需链接任何 CE 类。 */
public final class CraftEngineBridge {
    private final Plugin owner;
    public record GlyphMetrics(int advance, int height, int bearing) { }
    private record CachedGlyph(Object definition, GlyphMetrics metrics) { }
    private final ConcurrentHashMap<String, CachedGlyph> metrics = new ConcurrentHashMap<>();
    private volatile boolean compactPackReady;
    public CraftEngineBridge(Plugin owner) { this.owner = owner; }
    private ClassLoader loader() {
        Plugin ce = owner.getServer().getPluginManager().getPlugin("CraftEngine");
        return ce == null || !ce.isEnabled() ? null : ce.getClass().getClassLoader();
    }
    public boolean available() { return loader() != null; }
    public Optional<Component> image(String id, int row, int column) {
        ClassLoader loader = loader(); if (loader == null) return Optional.empty();
        try {
            Class<?> engineClass = Class.forName("net.momirealms.craftengine.core.plugin.CraftEngine", true, loader);
            Object engine = engineClass.getMethod("instance").invoke(null);
            Object fonts = engineClass.getMethod("fontManager").invoke(engine);
            Class<?> key = Class.forName("net.momirealms.craftengine.core.util.Key", true, loader);
            Object imageKey = key.getMethod("of", String.class).invoke(null, id);
            Class<?> manager = Class.forName("net.momirealms.craftengine.core.font.FontManager", true, loader);
            Optional<?> image = (Optional<?>) manager.getMethod("imageById", key).invoke(fonts, imageKey);
            if (image.isEmpty()) return Optional.empty();
            Object component = Class.forName("net.momirealms.craftengine.core.font.Image", true, loader)
                    .getMethod("componentAt", int.class, int.class).invoke(image.get(), row, column);
            Class<?> helper = Class.forName("net.momirealms.craftengine.core.util.AdventureHelper", true, loader);
            Method serialize = java.util.Arrays.stream(helper.getMethods()).filter(m -> m.getName().equals("componentToJson")
                    && m.getParameterCount() == 1).findFirst().orElseThrow();
            return Optional.of(GsonComponentSerializer.gson().deserialize((String) serialize.invoke(null, component)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return Optional.empty(); }
    }
    public boolean bookAvailable() { return compactPackReady && image("handbookcore:paper_2", 0, 0).isPresent(); }
    /** CE 可选且由其资源包流程合并第三方图片的缩放字体。 */
    @SuppressWarnings("unchecked")
    public void installPackHook() {
        ClassLoader loader = loader(); if (loader == null) return;
        try {
            Class<? extends Event> event = (Class<? extends Event>) Class.forName(
                    "net.momirealms.craftengine.bukkit.api.event.AsyncResourcePackCacheEvent", true, loader);
            owner.getServer().getPluginManager().registerEvent(event, new Listener() { }, EventPriority.NORMAL,
                    (listener, value) -> {
                        try {
                            Class<?> engine = Class.forName("net.momirealms.craftengine.core.plugin.CraftEngine", true, loader);
                            Object fonts = engine.getMethod("fontManager").invoke(engine.getMethod("instance").invoke(null));
                            Class<?> manager = Class.forName("net.momirealms.craftengine.core.font.FontManager", true, loader);
                            var bitmaps = (java.util.Map<?, ?>)manager.getMethod("loadedBitmapImages").invoke(fonts);
                            Path root = owner.getDataFolder().toPath().resolve("compact-resource-pack").toAbsolutePath();
                            CompactFontPack.write(root, new java.util.LinkedHashSet<>(bitmaps.values()));
                            value.getClass().getMethod("registerExternalResourcePack", Path.class).invoke(value, root);
                            metrics.clear(); compactPackReady = !bitmaps.isEmpty();
                        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException error) {
                            compactPackReady = false;
                            owner.getLogger().warning("手册缩放字体生成失败，暂用文字目录：" + error.getMessage());
                        }
                    }, owner);
        } catch (ReflectiveOperationException | LinkageError error) {
            owner.getLogger().warning("当前 CE 缺少资源包缓存事件，手册暂用文字目录。");
        }
    }
    /** 与客户端 BitmapProvider 一样，按非透明最右像素计算 advance；CE 重载后按定义身份失效。 */
    public Optional<GlyphMetrics> metrics(net.kyori.adventure.key.Key font, int cp) {
        return metrics(font, cp, false);
    }
    public Optional<GlyphMetrics> metrics(net.kyori.adventure.key.Key font, int cp, boolean compact) {
        ClassLoader loader = loader(); if (loader == null) return Optional.empty();
        try {
            Class<?> engineClass = Class.forName("net.momirealms.craftengine.core.plugin.CraftEngine", true, loader);
            Object fonts = engineClass.getMethod("fontManager").invoke(engineClass.getMethod("instance").invoke(null));
            Class<?> key = Class.forName("net.momirealms.craftengine.core.util.Key", true, loader);
            Object fontKey = key.getMethod("of", String.class).invoke(null, font.asString());
            Class<?> manager = Class.forName("net.momirealms.craftengine.core.font.FontManager", true, loader);
            Optional<?> result = (Optional<?>) manager.getMethod("bitmapImageByCodepoint", key, int.class).invoke(fonts, fontKey, cp);
            if (result.isEmpty()) return Optional.empty();
            Object bitmap = result.get(); String cacheKey = font.asString() + '/' + cp + '/' + compact;
            CachedGlyph cached = metrics.get(cacheKey);
            if (cached != null && cached.definition() == bitmap) return Optional.of(cached.metrics());
            Class<?> type = bitmap.getClass(); int[][] grid = (int[][]) type.getMethod("codepointGrid").invoke(bitmap);
            int height = (int) type.getMethod("height").invoke(bitmap);
            if (compact) height = Math.max(1, BookScale.pixels(height));
            String file = (String) type.getMethod("file").invoke(bitmap);
            String[] parts = file.split(":", 2); if (parts.length != 2) return Optional.empty();
            Path resources = owner.getDataFolder().toPath().getParent().resolve("CraftEngine/resources");
            try (var packs = Files.list(resources)) {
                for (Path pack : packs.sorted().toList()) {
                    Path root = pack.resolve("resourcepack/assets").toAbsolutePath().normalize();
                    Path texture = root.resolve(parts[0] + "/textures/" + parts[1]).normalize();
                    if (!texture.startsWith(root) || !Files.isRegularFile(texture)) continue;
                    var png = ImageIO.read(texture.toFile()); if (png == null) continue;
                    int cellWidth = png.getWidth() / grid[0].length, cellHeight = png.getHeight() / grid.length;
                    for (int row = 0; row < grid.length; row++) for (int col = 0; col < grid[row].length; col++) if (grid[row][col] == cp) {
                        int right = 0, left = cellWidth;
                        for (int x = 0; x < cellWidth; x++) for (int y = 0; y < cellHeight; y++)
                            if ((png.getRGB(col * cellWidth + x, row * cellHeight + y) >>> 24) != 0) { right = Math.max(right, x + 1); left = Math.min(left, x); }
                        var value = new GlyphMetrics(Math.round(right * (float)height / cellHeight) + 1, height,
                                Math.round(left * (float)height / cellHeight));
                        if (metrics.size() > 8192) metrics.clear();
                        metrics.put(cacheKey, new CachedGlyph(bitmap, value)); return Optional.of(value);
                    }
                }
            }
            // 原版贴图由客户端继承，服务端包内没有副本；使用构建时生成的同源指标。
            if (parts[0].equals("minecraft") && parts[1].startsWith("item/") && parts[1].endsWith(".png")) {
                java.util.Properties icons = new java.util.Properties();
                try (var input = CraftEngineBridge.class.getResourceAsStream("/vanilla-icons.properties")) {
                    if (input != null) icons.load(input);
                }
                String value = icons.getProperty(parts[1].substring(5, parts[1].length()-4));
                if (value != null) {
                    int advance = Math.round((Integer.parseInt(value.split(",")[1])-1) * height / 32.0f)+1;
                    int bearing = Math.round(Integer.parseInt(value.split(",")[2]) * height / 32.0f);
                    var measured = new GlyphMetrics(advance, height, bearing);
                    metrics.put(cacheKey, new CachedGlyph(bitmap, measured)); return Optional.of(measured);
                }
            }
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError | RuntimeException ignored) { }
        return Optional.empty();
    }
    public ItemStack item(HandbookItem ref, Player player) {
        ClassLoader loader = loader(); if (loader == null || ref.craftEngineId().isEmpty()) return ref.fallback();
        try {
            Object definition = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, loader)
                    .getMethod("byId", String.class).invoke(null, ref.craftEngineId());
            if (definition != null) return ((ItemStack) definition.getClass().getMethod("buildBukkitItem", Player.class)
                    .invoke(definition, player)).clone();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
        return ref.fallback();
    }
}
