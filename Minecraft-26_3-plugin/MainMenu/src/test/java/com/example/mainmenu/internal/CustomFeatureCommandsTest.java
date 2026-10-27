package com.example.mainmenu.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class CustomFeatureCommandsTest {
    private static CustomFeatureCommands read(final String source) throws Exception {
        return read(source, new ArrayList<>());
    }

    private static CustomFeatureCommands read(final String source, final List<String> warnings) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return CustomFeatureCommands.read(yaml, warnings::add);
    }

    @Test
    void legacyCommandStillAppliesToEveryClickType() throws Exception {
        final var commands = read("command: ' /plt gui '");
        assertTrue(commands.clickable());
        for (final ClickType click : ClickType.values()) {
            assertEquals("plt gui", commands.command(click), click.name());
        }
        assertTrue(commands.overridesForConfig().isEmpty());
    }

    @Test
    void eachMouseGestureCanRunItsOwnCommand() throws Exception {
        final var commands = read("""
                command: fallback
                commands:
                  left: 'plt gui'
                  right: 'dq gui'
                  shift-left: 'signin gui'
                  shift-right: 'warpmenu'
                  middle: '/help'
                """);
        assertEquals("plt gui", commands.command(ClickType.LEFT));
        assertEquals("dq gui", commands.command(ClickType.RIGHT));
        assertEquals("signin gui", commands.command(ClickType.SHIFT_LEFT));
        assertEquals("warpmenu", commands.command(ClickType.SHIFT_RIGHT));
        assertEquals("help", commands.command(ClickType.MIDDLE));
    }

    @Test
    void emptyOverrideDisablesOnlyThatGestureAndMissingKeysUseTheDefault() throws Exception {
        final var commands = read("""
                command: 'plt gui'
                commands:
                  left: 'dq gui'
                  right: ''
                  middle: ' / '
                """);
        assertEquals("", commands.command(ClickType.RIGHT));
        assertEquals("", commands.command(ClickType.MIDDLE));
        assertEquals("plt gui", commands.command(ClickType.SHIFT_LEFT));
        assertEquals("plt gui", commands.command(ClickType.SHIFT_RIGHT));
        assertEquals("dq gui", commands.command(ClickType.LEFT));
    }

    @Test
    void overridesWorkWithNoDefaultAndAllEmptyCommandsAreNotClickable() throws Exception {
        final var rightOnly = read("commands: {right: 'warpmenu'}");
        assertTrue(rightOnly.clickable());
        assertEquals("warpmenu", rightOnly.command(ClickType.RIGHT));
        assertEquals("", rightOnly.command(ClickType.LEFT));
        assertEquals("", rightOnly.command(ClickType.SHIFT_RIGHT));
        assertFalse(read("command: '/'\ncommands: {left: '', right: ' '}").clickable());
        assertFalse(read("").clickable());
    }

    @Test
    void configurationMistakesAreReportedAndInvalidValuesDisableTheKnownKey() throws Exception {
        final List<String> warnings = new ArrayList<>();
        final var commands = read("""
                command: fallback
                commands:
                  right: [not, a, command]
                  lef: typo
                """, warnings);
        assertEquals(2, warnings.size());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("commands.lef")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("commands.right")));
        assertEquals("", commands.command(ClickType.RIGHT));
        assertEquals("fallback", commands.command(ClickType.LEFT));
    }

    @Test
    void savingAndReadingKeepsEmptyOverridesDistinctFromMissingKeys() throws Exception {
        final var original = read("command: fallback\ncommands: {right: '', shift-left: '/signin gui'}");
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("command", original.fallback());
        yaml.set("commands", original.overridesForConfig());
        final var restored = read(yaml.saveToString());
        for (final ClickType click : ClickType.values()) {
            assertEquals(original.command(click), restored.command(click), click.name());
        }
    }

    @Test
    void disabledClickReturnsBeforeClosingTheMenuOrSchedulingACommand() {
        final var feature = new CustomCommandFeature(null, "custom:test", "Test", Material.PAPER,
                new CustomFeatureCommands("plt gui", Map.of(ClickType.RIGHT, "")), List.of(""), 0);
        assertTrue(feature.clickable());
        assertTrue(feature.clickable(ClickType.LEFT));
        assertFalse(feature.clickable(ClickType.RIGHT));
        // A disabled click must return without touching the player or plugin.
        assertDoesNotThrow(() -> feature.onClick(null, ClickType.RIGHT));
    }

    @Test
    void anOverrideOnlyFeatureDoesNotShowTheMissingCommandWarning() {
        final var feature = new CustomCommandFeature(null, "custom:test", "Test", Material.PAPER,
                new CustomFeatureCommands("", Map.of(ClickType.RIGHT, "warpmenu")), List.of(""), 0);
        assertTrue(feature.clickable());
        assertFalse(feature.clickable(ClickType.LEFT));
        assertTrue(feature.description().isEmpty());
    }
}
