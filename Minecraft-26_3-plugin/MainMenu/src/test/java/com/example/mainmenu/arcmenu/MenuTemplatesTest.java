package com.example.mainmenu.arcmenu;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class MenuTemplatesTest {
    @TempDir
    Path directory;

    private static InputStream resource(final String name) {
        return MenuTemplatesTest.class.getClassLoader().getResourceAsStream(name);
    }

    @Test
    void installAndReloadPreserveEditedHomeBytes() throws IOException {
        final Path home = directory.resolve("mainmenu-home.yml");
        final String authored = "# Administrator's edited home\nfrontend: {custom: true}\n";
        Files.writeString(home, authored);
        MenuTemplates.install(directory, MenuTemplatesTest::resource);
        MenuTemplates.install(directory, MenuTemplatesTest::resource);
        assertEquals(authored, Files.readString(home));
        try (var files = Files.list(directory)) {
            assertEquals(MenuTemplates.IDS.size(), files.count());
        }
        assertTrue(Files.readString(directory.resolve("mainmenu-position.yml")).contains("schema-version: 1"));
    }

    @Test
    void missingBundledResourceFailsExplicitly() {
        assertThrows(IOException.class, () -> MenuTemplates.install(directory, ignored -> null));
        assertFalse(Files.exists(directory.resolve("mainmenu-home.yml")));
    }

    @Test
    void serializedMenusHaveUniqueIdsAndValidSharedCatalogContracts() throws IOException {
        final Set<String> menus = new HashSet<>();
        for (final String id : MenuTemplates.IDS) {
            final Map<String, Object> doc = document("arcmenu/" + id + ".yml");
            assertTrue(menus.add((String) doc.get("id")));
            assertEquals(id, doc.get("id"));
            assertEquals(1, doc.get("schema-version"));
            assertEquals(false, doc.get("main-menu"));
            assertEquals(List.of(), doc.get("open-commands"));
            assertEquals(id.startsWith("mainmenu-attribute") || List.of("mainmenu-manage", "mainmenu-position",
                    "mainmenu-custom").contains(id) ? "mainmenu.admin" : "mainmenu.use", doc.get("permission"));
            final Map<?, ?> canvas = (Map<?, ?>) doc.get("canvas");
            assertEquals(42.7, ((Number) canvas.get("pixels-per-block")).doubleValue());
            final Map<?, ?> front = (Map<?, ?>) doc.get("frontend");
            final Map<?, ?> back = (Map<?, ?>) doc.get("backend");
            final Set<Object> ids = new HashSet<>(front.keySet());
            assertTrue(front.size() > 0 && back.size() > 0);
            for (final Object key : back.keySet()) {
                assertTrue(ids.add(key), id + ": duplicate frontend/backend ID " + key);
            }
            for (final Object value : front.values()) {
                final Map<?, ?> element = (Map<?, ?>) value;
                assertNotNull(element.get("type"));
                assertTrue(((Number) ((Map<?, ?>) element.get("offset")).get("z")).intValue() >= -3);
                assertTrue(((Number) ((Map<?, ?>) element.get("offset")).get("z")).intValue() <= 3);
                if ("item".equals(element.get("type"))) {
                    assertNotEquals("minecraft:air", element.get("material"),
                            "ArcMenu 3.2 cannot build AIR item nodes");
                }
                assertFalse(element.containsKey("condition"), "Frontend conditions are unsupported in 3.2");
            }
            for (final Object value : back.values()) {
                final Map<?, ?> region = (Map<?, ?>) value;
                assertTrue(((Number) region.get("width")).doubleValue() > 0);
                assertTrue(((Number) region.get("height")).doubleValue() > 0);
            }
        }
    }

    @Test
    void homeHasEditableFeatureBindingsAndDoesNotEscalateCommands() throws IOException {
        final Map<String, Object> home = document("arcmenu/mainmenu-home.yml");
        final Map<?, ?> backend = (Map<?, ?>) home.get("backend");
        for (final String alias : List.of("town", "wallet", "music", "notice")) {
            final Map<?, ?> region = (Map<?, ?>) backend.get(alias + "-open");
            assertEquals("{meta:mm-bind-" + alias + "-available} == true", region.get("condition"));
            final Map<?, ?> clicks = (Map<?, ?>) region.get("actions");
            for (final String click : List.of("left", "right", "shift-left", "shift-right")) {
                assertEquals(List.of("open: mainmenu:feature {meta:mm-bind-" + alias + "-id} " + click),
                        clicks.get(click));
            }
        }
        for (final String id : MenuTemplates.IDS) {
            try (var in = resource("arcmenu/" + id + ".yml")) {
                final String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(text.contains("op:"));
                assertFalse(text.contains("console:"));
                assertFalse(text.contains("give-item:"));
                assertFalse(text.contains("take-money:"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document(final String name) throws IOException {
        final LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var in = resource(name)) {
            assertNotNull(in, name);
            return (Map<String, Object>) new Yaml(new SafeConstructor(options)).load(in);
        }
    }
}
