package com.example.handbookcore;

import com.example.handbookcore.internal.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ContentBootstrapTest {
    private static YamlConfiguration read(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file.toFile()); return yaml;
    }

    private static Set<String> ids(YamlConfiguration yaml, String field) {
        Set<String> ids = new HashSet<>();
        for (var item : yaml.getMapList(field)) ids.add((String) item.get("id"));
        return ids;
    }

    private Path legacyFile(Path dataDirectory) throws Exception {
        Path file = dataDirectory.resolve("content/server.yml");
        Files.createDirectories(file.getParent());
        try (var resource = getClass().getResourceAsStream("/legacy-server.yml")) {
            Files.copy(Objects.requireNonNull(resource), file);
        }
        return file;
    }

    @Test void firstInstallSeedsOnlyOneLogAndDeletingContentSurvivesRestart(@TempDir Path dataDirectory) throws Exception {
        assertNull(ContentBootstrap.prepare(dataDirectory));
        Path directory = dataDirectory.resolve("content");
        var content = read(directory.resolve("server.yml"));
        assertEquals(Set.of("handbook:updates"), ids(content, "categories"));
        assertEquals(Set.of("handbook:update-2026-10-26"), ids(content, "entries"));
        Files.delete(directory.resolve("server.yml"));
        ContentBootstrap.prepare(dataDirectory);
        assertTrue(new ContentLoader().load(directory).entries().isEmpty());
        Files.delete(directory);
        ContentBootstrap.prepare(dataDirectory);
        assertTrue(new ContentLoader().load(directory).categories().isEmpty());
    }

    @Test void upgradeBacksUpOriginalAndRemovesOnlyBundledGameplay(@TempDir Path dataDirectory) throws Exception {
        Path file = legacyFile(dataDirectory);
        byte[] original = Files.readAllBytes(file);
        var previous = read(file);
        Path backup = ContentBootstrap.prepare(dataDirectory);
        assertNotNull(backup);
        assertArrayEquals(original, Files.readAllBytes(backup));
        assertFalse(backup.startsWith(file.getParent()));
        var remaining = read(file);
        assertEquals(Set.of("handbook:updates"), ids(remaining, "categories"));
        assertEquals(List.of(previous.getMapList("entries").getLast()), remaining.getMapList("entries"));
        assertNull(ContentBootstrap.prepare(dataDirectory));
        try (var backups = Files.list(backup.getParent())) { assertEquals(1, backups.count()); }
    }

    @Test void customArticlesAndTheirReferencedCategoriesArePreserved(@TempDir Path dataDirectory) throws Exception {
        Path file = legacyFile(dataDirectory);
        Files.writeString(file, """
                  - id: server:town-rules
                    category: handbook:town
                    title: 自定义镇规
                    pages:
                      - blocks: ['管理员自己的正文']
                """, StandardOpenOption.APPEND);
        Path custom = file.resolveSibling("custom.yaml");
        String customContent = """
                entries:
                  - id: server:recipe
                    category: handbook:farming
                    title: 自定义配方
                    pages:
                      - blocks: ['管理员自己的配方']
                """;
        Files.writeString(custom, customContent);
        ContentBootstrap.prepare(dataDirectory);
        var content = read(file);
        assertEquals(Set.of("handbook:updates", "handbook:town", "handbook:farming"), ids(content, "categories"));
        assertEquals(Set.of("handbook:update-2026-10-26", "server:town-rules"), ids(content, "entries"));
        assertEquals(customContent, Files.readString(custom));
        assertEquals(Set.of("server:recipe"), ids(read(custom), "entries"));
    }

    @Test void existingContentDirectoryDoesNotReceiveAnExtraBuiltinLog(@TempDir Path dataDirectory) throws Exception {
        Files.createDirectories(dataDirectory.resolve("content"));
        ContentBootstrap.prepare(dataDirectory);
        assertTrue(new ContentLoader().load(dataDirectory.resolve("content")).entries().isEmpty());
        assertFalse(Files.exists(dataDirectory.resolve("content/server.yml")));
    }

    @Test void laterOperatorChangesAreNotMigratedAgain(@TempDir Path dataDirectory) throws Exception {
        ContentBootstrap.prepare(dataDirectory);
        Path file = dataDirectory.resolve("content/server.yml");
        Files.delete(file);
        legacyFile(dataDirectory);
        byte[] operatorContent = Files.readAllBytes(file);
        assertNull(ContentBootstrap.prepare(dataDirectory));
        assertArrayEquals(operatorContent, Files.readAllBytes(file));
    }

    @Test void invalidContentLeavesOriginalAndInitializationStateUntouched(@TempDir Path dataDirectory) throws Exception {
        Path file = legacyFile(dataDirectory);
        byte[] original = Files.readAllBytes(file);
        Path broken = file.resolveSibling("broken.yml");
        Files.writeString(broken, "entries: wrong-type\n");
        assertThrows(Exception.class, () -> ContentBootstrap.prepare(dataDirectory));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(Files.exists(dataDirectory.resolve(".content-initialized")));
        assertFalse(Files.exists(dataDirectory.resolve("backups")));
        Files.delete(broken);
        assertNotNull(ContentBootstrap.prepare(dataDirectory));
        assertEquals(Set.of("handbook:update-2026-10-26"), ids(read(file), "entries"));
    }
}
