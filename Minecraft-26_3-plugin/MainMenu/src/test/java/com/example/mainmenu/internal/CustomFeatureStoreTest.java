package com.example.mainmenu.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomFeatureStoreTest {
    @TempDir
    Path directory;

    @Test
    void creatingAnotherFeaturePreservesExistingCommandsAcrossReload() throws Exception {
        final Path file = directory.resolve("custom-features.yml");
        final String authored = """
                features:
                  custom:existing:
                    name: 称号
                    icon: NAME_TAG
                    command: 'plt gui'
                    commands:
                      right: ''
                      shift-left: 'dq gui'
                      middle: 'signin gui'
                    order: 10
                """;
        Files.writeString(file, authored);
        final var store = new CustomFeatureStore(null, directory.toFile(), Logger.getAnonymousLogger());
        store.load();
        assertEquals(authored, Files.readString(file), "Loading must not rewrite administrator edits");
        final String createdId = store.create("传送石");
        assertNotNull(createdId);

        final var saved = YamlConfiguration.loadConfiguration(file.toFile());
        final var existing = saved.getConfigurationSection("features.custom:existing");
        assertNotNull(existing);
        assertEquals("", existing.getString("commands.right"));
        assertEquals("dq gui", existing.getString("commands.shift-left"));
        assertEquals("signin gui", existing.getString("commands.middle"));
        assertEquals("plt gui", existing.getString("command"));
        final var created = saved.getConfigurationSection("features." + createdId);
        assertNotNull(created);
        assertEquals("传送石", created.getString("name"));
        assertEquals(20, created.getInt("order"));
        assertFalse(CustomFeatureCommands.read(created, failMessage -> fail(failMessage)).clickable());

        store.load();
        existing.set("commands.left", "");
        existing.set("commands.right", "warpmenu");
        saved.save(file.toFile());
        store.load();
        store.create("签到");
        final var reloaded = YamlConfiguration.loadConfiguration(file.toFile())
                .getConfigurationSection("features.custom:existing");
        assertNotNull(reloaded);
        final var updated = CustomFeatureCommands.read(reloaded, failMessage -> fail(failMessage));
        assertEquals("", updated.command(ClickType.LEFT));
        assertEquals("warpmenu", updated.command(ClickType.RIGHT));
        assertEquals("dq gui", updated.command(ClickType.SHIFT_LEFT));
        assertEquals("plt gui", updated.command(ClickType.SHIFT_RIGHT));
    }
}
