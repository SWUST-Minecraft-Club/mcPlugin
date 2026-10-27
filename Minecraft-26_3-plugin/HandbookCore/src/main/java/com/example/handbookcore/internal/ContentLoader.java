package com.example.handbookcore.internal;

import com.example.handbookcore.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 配置读取不修改活动目录；调用方在全部成功后一次替换。 */
public final class ContentLoader {
    public HandbookContribution load(Path directory) throws IOException, InvalidConfigurationException {
        List<HandbookCategory> categories = new ArrayList<>(); List<HandbookEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")).sorted().toList()) {
                if (Files.size(path) > 2 * 1024 * 1024) throw new IOException("手册文件超过 2 MiB：" + path.getFileName());
                YamlConfiguration yaml = new YamlConfiguration(); yaml.load(path.toFile());
                try {
                    for (Object raw : yamlList(yaml, "categories")) {
                        Map<String, Object> c = map(raw);
                        categories.add(new HandbookCategory(key(c, "id"), required(c, "title"), string(c, "description", ""),
                                new HandbookIcon(material(c, "icon", Material.BOOK), string(c, "image", ""), string(c, "bookmark-image", string(c, "image", ""))),
                                integer(c, "order", 0), string(c, "permission", "")));
                    }
                    for (Object raw : yamlList(yaml, "entries")) entries.add(entry(map(raw)));
                } catch (IllegalArgumentException error) {
                    throw new InvalidConfigurationException(path.getFileName() + "：" + error.getMessage(), error);
                }
            }
        }
        return new HandbookContribution(categories, entries);
    }
    private static List<?> yamlList(YamlConfiguration yaml, String field) {
        Object value = yaml.get(field, List.of());
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException(field + " 应为列表");
        return values;
    }
    private HandbookEntry entry(Map<String, Object> e) {
        List<HandbookPage> pages = new ArrayList<>();
        for (Object value : list(e, "pages")) {
            Map<String, Object> page = map(value); List<HandbookBlock> blocks = new ArrayList<>();
            for (Object raw : list(page, "blocks")) {
                if (raw instanceof String text) { blocks.add(HandbookBlock.text(text)); continue; }
                Map<String, Object> b = map(raw);
                blocks.add(switch (required(b, "type")) {
                    case "text" -> new HandbookBlock.Text(required(b, "text"));
                    case "image" -> new HandbookBlock.Image(required(b, "image"), integer(b, "width", 64),
                            integer(b, "height", 32), required(b, "alt"));
                    case "item" -> new HandbookBlock.Item(string(b, "ce-item", "").isEmpty()
                            ? HandbookItem.vanilla(material(b, "material", Material.BOOK))
                            : HandbookItem.craftEngine(required(b, "ce-item"), material(b, "material", Material.BOOK)), required(b, "caption"));
                    case "link" -> new HandbookBlock.Link(required(b, "label"), key(b, "entry"));
                    case "url" -> new HandbookBlock.Url(required(b, "label"), required(b, "url"));
                    case "command" -> new HandbookBlock.Command(required(b, "label"), required(b, "command"), bool(b, "close-before-run", true));
                    case "space" -> new HandbookBlock.Space(integer(b, "lines", 1));
                    default -> throw new IllegalArgumentException("未知内容块：" + b.get("type"));
                });
            }
            pages.add(new HandbookPage(string(page, "title", ""), blocks));
        }
        List<String> keywords = list(e, "keywords").stream().map(o -> {
            if (!(o instanceof String text)) throw new IllegalArgumentException("keywords 应为文字列表");
            return text;
        }).toList();
        return new HandbookEntry(key(e, "id"), key(e, "category"), required(e, "title"), string(e, "summary", ""),
                keywords, integer(e, "order", 0), string(e, "permission", ""), pages);
    }
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("应为键值配置");
        Map<String, Object> map = new LinkedHashMap<>(); raw.forEach((k, v) -> map.put(String.valueOf(k), v)); return map;
    }
    private static List<?> list(Map<String, Object> m, String key) {
        Object value = m.getOrDefault(key, List.of());
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(key + " 应为列表"); return list;
    }
    private static String string(Map<String, Object> m, String key, String fallback) {
        Object value = m.getOrDefault(key, fallback);
        if (!(value instanceof String s)) throw new IllegalArgumentException(key + " 应为文字"); return s;
    }
    private static String required(Map<String, Object> m, String key) {
        String value = string(m, key, ""); if (value.isBlank()) throw new IllegalArgumentException("缺少 " + key); return value;
    }
    private static int integer(Map<String, Object> m, String key, int fallback) {
        Object value = m.getOrDefault(key, fallback);
        if (!(value instanceof Integer i)) throw new IllegalArgumentException(key + " 应为整数"); return i;
    }
    private static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object value = m.getOrDefault(key, fallback);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException(key + " 应为布尔值"); return b;
    }
    private static NamespacedKey key(Map<String, Object> m, String field) {
        String raw = required(m, field); NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null || !raw.contains(":")) throw new IllegalArgumentException("无效 namespaced id：" + raw); return key;
    }
    private static Material material(Map<String, Object> m, String field, Material fallback) {
        Material value = Material.matchMaterial(string(m, field, fallback.name()));
        if (value == null || !value.isItem() || value.isAir()) throw new IllegalArgumentException("无效物品材质：" + m.get(field)); return value;
    }
}
