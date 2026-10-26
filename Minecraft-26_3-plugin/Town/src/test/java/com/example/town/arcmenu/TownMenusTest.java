package com.example.town.arcmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.town.TownConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TownMenusTest {

    private static final TownConfig CONFIG = new TownConfig(
            2, 0, 2, 12, Pattern.compile("[0-9A-Za-z_\\u4e00-\\u9fa5]+"),
            72L * 3600_000L, 30L * 60_000L, "", 1000, 1, 9,
            Set.of(), true, 256, 50, "", Material.LODESTONE,
            new TownConfig.Protection(true, true, true, true, true, true, true, true, true, true),
            40, java.util.List.of(), 10, Map.of());

    @Test
    void generatesElevenValidSelfContainedMenus() {
        Map<String, MenuBuilder> menus = TownMenus.all(CONFIG);
        assertEquals(11 + TownOperationMenus.all().size(), menus.size());
        for (Map.Entry<String, MenuBuilder> entry : menus.entrySet()) {
            String json = entry.getValue().dump(entry.getKey());
            JsonObject doc = JsonParser.parseString(json).getAsJsonObject();
            assertEquals(entry.getKey(), doc.get("id").getAsString());
            assertFalse(doc.get("main-menu").getAsBoolean());
            assertFalse(json.contains("\"open: "), entry.getKey() + " 不应绕过 TownUi 初始化");
            JsonObject frontend = doc.getAsJsonObject("frontend");
            JsonObject backend = doc.getAsJsonObject("backend");
            for (String id : frontend.keySet()) {
                assertFalse(backend.has(id), "前后端 ID 冲突: " + id);
                assertFalse(frontend.getAsJsonObject(id).has("condition"));
            }
            for (String id : backend.keySet()) {
                JsonObject region = backend.getAsJsonObject(id);
                if (!region.has("type") || !"Arcitx".equals(region.get("type").getAsString())) {
                    continue;
                }
                assertTrue(frontend.has(region.get("text").getAsString()));
                String submit = region.get("submit").getAsString();
                assertTrue(backend.has(submit), "Arcitx submit 不存在: " + submit);
                assertTrue(backend.getAsJsonObject(submit).has("actions"));
            }
        }
    }

    @Test
    void inputsUseApiBridgeAndMemberRowsKeepRawNames() {
        Map<String, MenuBuilder> menus = TownMenus.all(CONFIG);
        for (MenuBuilder builder : menus.values()) {
            assertFalse(builder.dump("x").contains("Arcitx"), "Arcitx 已由 API 文本输入替代");
        }
        JsonObject members = doc(TownMenus.MEMBERS);
        JsonObject backend = members.getAsJsonObject("backend");
        assertEquals("command: town ui-input invite",
                backend.getAsJsonObject("inv-input").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals("command: town ui-input invite",
                backend.getAsJsonObject("inv-go").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals("command: town member {meta:member_0_name}",
                backend.getAsJsonObject("mem-manage-0").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals(8, backend.keySet().stream().filter(id -> id.startsWith("mem-manage-")).count());
        JsonObject create = doc(TownMenus.CREATE).getAsJsonObject("backend");
        assertEquals("command: town ui-input name",
                create.getAsJsonObject("in-name").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
    }

    @Test
    void claimHasFortyNineCellsWithRealMapImageAndDragButtons() {
        JsonObject doc = doc(TownMenus.CLAIM);
        JsonObject backend = doc.getAsJsonObject("backend");
        JsonObject frontend = doc.getAsJsonObject("frontend");
        assertEquals(49, backend.keySet().stream().filter(id -> id.startsWith("cellgo-")).count());
        assertEquals(49, frontend.keySet().stream().filter(id -> id.startsWith("cellmark-")).count(),
                "每格都要有状态角标");
        JsonObject mapImage = frontend.getAsJsonObject("map-image");
        assertEquals("image", mapImage.get("type").getAsString());
        assertEquals("/town/map/%arcmenu_meta_map_key%.png", mapImage.get("source").getAsString());
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                JsonObject actions = backend.getAsJsonObject("cellgo-" + r + "-" + c)
                        .getAsJsonObject("actions");
                assertEquals("command: town confirm claimmap " + r + " " + c,
                        actions.getAsJsonArray("left").get(0).getAsString());
                assertEquals("command: town confirm unclaimat " + r + " " + c,
                        actions.getAsJsonArray("right").get(0).getAsString());
            }
        }
        assertEquals("command: town claimmap move -3 0",
                backend.getAsJsonObject("move-left").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals("command: town claimmap move 0 3",
                backend.getAsJsonObject("move-down").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals("command: town claimmap center",
                backend.getAsJsonObject("recenter").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
    }

    @Test
    void buffsHaveSixDynamicSlotsAndPagination() {
        JsonObject backend = doc(TownMenus.BUFFS).getAsJsonObject("backend");
        assertEquals(6, backend.keySet().stream().filter(id -> id.startsWith("bf-buy-")).count());
        assertEquals("command: town buffs {meta:next_page}",
                backend.getAsJsonObject("page-next").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
        assertEquals("command: town confirm buff {meta:bf_0_id}",
                backend.getAsJsonObject("bf-buy-0").getAsJsonObject("actions")
                        .getAsJsonArray("all").get(0).getAsString());
    }

    private static JsonObject doc(String id) {
        MenuBuilder builder = TownMenus.all(CONFIG).get(id);
        return JsonParser.parseString(builder.dump(id)).getAsJsonObject();
    }
}
