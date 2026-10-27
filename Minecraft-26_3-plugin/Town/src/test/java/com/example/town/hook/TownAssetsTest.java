package com.example.town.hook;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TownAssetsTest {
    @TempDir Path directory;

    private Path definition(String material) throws IOException {
        Path file=directory.resolve("town.yml");
        Files.writeString(file,"""
                # 运营自定义注释
                items:
                  town:town_hall:
                    material: paper
                    data:
                      item_name: 自定义政厅
                  town:surveyor:
                    material: %s
                    model: town:item/custom_surveyor
                    data:
                      item_name: 自定义测绘尺
                """.formatted(material));
        return file;
    }

    @Test void migratesOnlyLegacyMaterialAndBacksUpExactOriginal() throws IOException {
        Path file=definition("stick");
        String original=Files.readString(file);
        assertTrue(TownAssets.migrateSurveyorMaterial(file));
        assertEquals(original,Files.readString(directory.resolve("town.yml.before-surveyor-flint.bak")));
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("flint",yaml.getString("items.town:surveyor.material"));
        assertEquals("town:item/custom_surveyor",yaml.getString("items.town:surveyor.model"));
        assertEquals("自定义测绘尺",yaml.getString("items.town:surveyor.data.item_name"));
        assertEquals("自定义政厅",yaml.getString("items.town:town_hall.data.item_name"));
        assertTrue(Files.readString(file).contains("# 运营自定义注释"));
        String updated=Files.readString(file);
        assertFalse(TownAssets.migrateSurveyorMaterial(file));
        assertEquals(updated,Files.readString(file));
        assertEquals(original,Files.readString(directory.resolve("town.yml.before-surveyor-flint.bak")));
    }

    @Test void acceptsNamespacedLegacyMaterial() throws IOException {
        Path file=definition("minecraft:stick");
        assertTrue(TownAssets.migrateSurveyorMaterial(file));
        assertEquals("flint",YamlConfiguration.loadConfiguration(file.toFile()).getString("items.town:surveyor.material"));
    }

    @Test void preservesCustomBaseAndDoesNotCreateBackup() throws IOException {
        Path file=definition("amethyst_shard");
        String original=Files.readString(file);
        assertFalse(TownAssets.migrateSurveyorMaterial(file));
        assertEquals(original,Files.readString(file));
        assertFalse(Files.exists(directory.resolve("town.yml.before-surveyor-flint.bak")));
    }

    @Test void preservesInvalidConfigurationForOperatorRepair() throws IOException {
        Path file=directory.resolve("town.yml");
        Files.writeString(file,"items: [broken");
        assertThrows(IOException.class,()->TownAssets.migrateSurveyorMaterial(file));
        assertEquals("items: [broken",Files.readString(file));
        assertFalse(Files.exists(directory.resolve("town.yml.before-surveyor-flint.bak")));
    }
}
