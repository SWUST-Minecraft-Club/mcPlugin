package com.example.handbookcore.internal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 只初始化一次更新日志；升级时备份并摘除旧核心自带的玩法示例。 */
public final class ContentBootstrap {
    private static final Set<String> LEGACY_ENTRIES = Set.of(
            "handbook:stardew-intro", "handbook:town-intro", "handbook:farming-intro");
    private static final Set<String> LEGACY_CATEGORIES = Set.of(
            "handbook:stardew", "handbook:town", "handbook:farming");

    private ContentBootstrap() { }

    /** 返回升级备份路径；没有修改已有正文时返回 null。 */
    public static Path prepare(Path dataDirectory) throws IOException, InvalidConfigurationException {
        Path directory = dataDirectory.resolve("content");
        Path marker = dataDirectory.resolve(".content-initialized");
        boolean fresh = Files.notExists(directory);
        Files.createDirectories(directory);
        if (Files.exists(marker)) return null;

        Path backup = null;
        if (fresh) {
            try (var resource = ContentBootstrap.class.getResourceAsStream("/content/server.yml")) {
                if (resource == null) throw new IOException("缺少默认更新日志资源");
                Files.copy(resource, directory.resolve("server.yml"));
            }
        } else {
            backup = removeLegacyExamples(dataDirectory, directory);
        }
        Files.writeString(marker, "Beta_0_1_1\n", StandardOpenOption.CREATE_NEW);
        return backup;
    }

    private static Path removeLegacyExamples(Path dataDirectory, Path directory)
            throws IOException, InvalidConfigurationException {
        Path file = directory.resolve("server.yml");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        YamlConfiguration yaml = ContentLoader.read(file);
        List<?> entries = list(yaml, "entries", file);
        List<?> keptEntries = entries.stream().filter(value -> !hasId(value, LEGACY_ENTRIES)).toList();
        // 迁移只读 YAML，不构建物品；先读取全部文件的引用，避免删掉自定义文章仍在使用的分类。
        Set<String> referencedCategories = new HashSet<>();
        for (Path path : ContentLoader.files(directory)) {
            var content = path.equals(file) ? yaml : ContentLoader.read(path);
            for (Object value : path.equals(file) ? keptEntries : list(content, "entries", path)) {
                if (!(value instanceof Map<?, ?> entry) || !(entry.get("category") instanceof String category))
                    throw new InvalidConfigurationException(path.getFileName() + "：条目缺少 category");
                referencedCategories.add(category);
            }
        }
        List<?> categories = list(yaml, "categories", file);
        List<?> keptCategories = categories.stream().filter(value -> !hasId(value, LEGACY_CATEGORIES)
                || referencedCategories.contains(((Map<?, ?>) value).get("id"))).toList();
        if (entries.size() == keptEntries.size() && categories.size() == keptCategories.size()) return null;

        Path backups = dataDirectory.resolve("backups");
        Files.createDirectories(backups);
        Path backup = Files.createTempFile(backups, "server-before-Beta_0_1_1-", ".yml");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        yaml.set("entries", keptEntries);
        yaml.set("categories", keptCategories);
        Path pending = Files.createTempFile(directory, ".server-", ".tmp");
        try {
            yaml.save(pending.toFile());
            try {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
        return backup;
    }

    private static boolean hasId(Object value, Set<String> ids) {
        return value instanceof Map<?, ?> map && map.get("id") instanceof String id && ids.contains(id);
    }

    private static List<?> list(YamlConfiguration yaml, String field, Path path) throws InvalidConfigurationException {
        try { return ContentLoader.yamlList(yaml, field); }
        catch (IllegalArgumentException error) {
            throw new InvalidConfigurationException(path.getFileName() + "：" + error.getMessage(), error);
        }
    }
}
