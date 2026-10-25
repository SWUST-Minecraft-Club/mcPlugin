package com.example.town.arcmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class MenuBuilderTest {

    @Test
    void rejectsDuplicateIdsAcrossFrontendAndBackend() {
        MenuBuilder menu = new MenuBuilder();
        menu.rect("shared", 0, 0, 10, 10, TownMenus.BG, 0);
        assertThrows(IllegalArgumentException.class,
                () -> menu.region("shared", 0, 0, 10, 10, null));
    }

    @Test
    void rejectsOutOfBoundsAndDynamicLeftAlignment() {
        MenuBuilder menu = new MenuBuilder();
        assertThrows(IllegalArgumentException.class,
                () -> menu.rect("bad", 795, 0, 10, 10, TownMenus.BG, 0));
        assertThrows(IllegalArgumentException.class,
                () -> menu.text("dynamic", "{meta:name}", 10, 20, 10, 5,
                        MenuBuilder.Align.LEFT, null));
    }

    @Test
    void dumpsCompleteMenuShellAndCloseEvent() {
        MenuBuilder menu = new MenuBuilder();
        menu.rect("background", 0, 0, 800, 450, TownMenus.BG, 0);
        JsonObject doc = JsonParser.parseString(menu.dump("town-test")).getAsJsonObject();

        assertEquals(1, doc.get("schema-version").getAsInt());
        assertEquals("town-test", doc.get("id").getAsString());
        assertFalse(doc.get("main-menu").getAsBoolean());
        assertEquals(42.7, doc.getAsJsonObject("canvas").get("pixels-per-block").getAsDouble());
        assertEquals(0.70, MenuBuilder.WINDOW_SCALE);
        assertTrue(doc.getAsJsonArray("open-commands").isEmpty());
        assertEquals("command: town ui-closed town-test",
                doc.getAsJsonObject("events").getAsJsonArray("close").get(0).getAsString());
    }

    @Test
    void imageKeepsMeasuredGeometryWithoutDoubleCompensation() {
        MenuBuilder menu = new MenuBuilder();
        menu.image("map-image", "/town/map/%arcmenu_meta_map_key%.png",
                20, 68, 326, 326, 1, 20);
        JsonObject image = JsonParser.parseString(menu.dump("town-claim"))
                .getAsJsonObject().getAsJsonObject("frontend").getAsJsonObject("map-image");
        assertEquals(62.136, image.get("width").getAsDouble(), 0.001);
        assertEquals(62.136, image.get("height").getAsDouble(), 0.001);
        assertEquals(-2, image.getAsJsonObject("offset").get("z").getAsInt());
        assertEquals("/town/map/%arcmenu_meta_map_key%.png", image.get("source").getAsString());
    }
}
