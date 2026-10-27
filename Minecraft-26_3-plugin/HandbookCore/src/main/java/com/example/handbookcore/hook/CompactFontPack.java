package com.example.handbookcore.hook;

import com.example.handbookcore.render.BookScale;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.key.Key;

/** 在 CE 缓存资源包事件中注入副本，保留其他界面的原字体与图片大小。 */
final class CompactFontPack {
    private CompactFontPack() { }
    static void write(Path root, Collection<?> bitmaps) throws ReflectiveOperationException, IOException {
        Map<Key, List<JsonObject>> fonts = new TreeMap<>(Comparator.comparing(Key::asString));
        for (Object bitmap : bitmaps) {
            Class<?> type = bitmap.getClass();
            Key font = Key.key(type.getMethod("font").invoke(bitmap).toString());
            if (font.namespace().equals("handbookcore") && Set.of("paper", "body", "heading", "button", "icons",
                    "icons_small", "bookmarks", "hit").contains(font.value())) continue; // 核心副本已随安装包提供。
            JsonObject provider = new JsonObject(); provider.addProperty("type", "bitmap");
            provider.addProperty("file", (String)type.getMethod("file").invoke(bitmap));
            provider.addProperty("height", (int)type.getMethod("height").invoke(bitmap));
            provider.addProperty("ascent", (int)type.getMethod("ascent").invoke(bitmap));
            JsonArray chars = new JsonArray();
            for (int[] row : (int[][])type.getMethod("codepointGrid").invoke(bitmap)) chars.add(new String(row, 0, row.length));
            provider.add("chars", chars);
            fonts.computeIfAbsent(font, ignored -> new ArrayList<>()).add(provider);
        }
        // 每次生成从当前快照重建；已注销插件不会留下旧字体定义。
        if (Files.exists(root)) try (var files = Files.walk(root)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        Files.createDirectories(root);
        for (var entry : fonts.entrySet()) for (int offset = 0; offset < 9; offset++) {
            JsonArray providers = new JsonArray();
            for (JsonObject source : entry.getValue()) providers.add(resize(source, offset));
            JsonObject document = new JsonObject(); document.add("providers", providers);
            Key target = BookScale.font(entry.getKey(), offset);
            Path file = root.resolve("assets/" + target.namespace() + "/font/" + target.value() + ".json");
            Files.createDirectories(file.getParent()); Files.writeString(file, document.toString());
        }
    }
    static JsonObject resize(JsonObject source, int offset) {
        JsonObject result = source.deepCopy();
        int height = Math.max(1, BookScale.pixels(source.get("height").getAsInt()));
        result.addProperty("height", height);
        result.addProperty("ascent", Math.min(height, 7 - BookScale.pixels(7-source.get("ascent").getAsInt()) - offset));
        return result;
    }
}
