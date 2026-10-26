package com.example.mainmenu.sidecard;

import static org.junit.jupiter.api.Assertions.*;

import com.example.mainmenu.api.MenuSidecard;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidecardSettingsTest {
    @Test void bundledDefaultsAreOffAndDemoDataIsNeverEnabledByDefault() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("sidecards.yml")) {
            assertNotNull(input);
            final var settings = SidecardSettings.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(settings.enabled());
            assertEquals(6, settings.cards().size());
            assertEquals(List.of("yaml:economy", "yaml:server", "yaml:shortcuts"),
                    settings.cards().stream().filter(SidecardSettings.Definition::enabled).map(SidecardSettings.Definition::id).toList());
            assertEquals(SidecardLayout.Area.defaults(), settings.area());
            assertFalse(settings.menus().contains("mainmenu-manage"));
        }
    }

    @Test void apiOverrideIdsKeepDotsAndSlashes() throws Exception {
        final var settings = SidecardSettings.parse("""
                enabled: true
                overrides:
                  'my.plugin:skills/mining': {enabled: false, side: right, order: 42}
                """);
        final var override = settings.overrides().get("my.plugin:skills/mining");
        assertNotNull(override);
        assertFalse(override.enabled());
        assertEquals(42, override.order());
        assertEquals(MenuSidecard.Side.RIGHT, override.side());
    }

    @Test void invalidLayoutsColorsActionsAndDuplicateButtonsRejectTheSnapshot() {
        for (final String source : List.of("layout: {height: 999}", "theme: {border: red}", """
                cards:
                  test:
                    actions: ['op: stop']
                """, """
                cards:
                  test:
                    buttons:
                      - {id: open, label: A}
                      - {id: open, label: B}
                """)) assertThrows(IllegalArgumentException.class, () -> SidecardSettings.parse(source));
    }

    @Test void contentHeightDependsOnStructureAndNeverOnScores() {
        final var a = new MenuSidecard.Content("技能", null, List.of(MenuSidecard.Row.progress("耕种", "Lv.8", .8)));
        final var b = new MenuSidecard.Content("技能", null, List.of(MenuSidecard.Row.progress("耕种", "Lv.999", 1)));
        assertEquals(SidecardService.height(a, 88), SidecardService.height(b, 88));
        final var tall = new MenuSidecard.Content("技能", null, List.of(MenuSidecard.Row.progress("耕种", "8", .8),
                MenuSidecard.Row.progress("采矿", "6", .6), MenuSidecard.Row.progress("钓鱼", "5", .5)));
        assertEquals(156, SidecardService.height(tall, 88));
        assertThrows(IllegalArgumentException.class, () -> new MenuSidecard.Row(net.kyori.adventure.text.Component.empty(),
                net.kyori.adventure.text.Component.empty(), Double.NaN));
    }
}
