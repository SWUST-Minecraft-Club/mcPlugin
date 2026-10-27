package com.example.handbookcore;

import com.example.handbookcore.render.*;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactBookTest {
    private final Properties metrics = new Properties();
    CompactBookTest() throws Exception {
        try (var input = getClass().getResourceAsStream("/compact-glyphs.properties")) { metrics.load(input); }
    }
    private void measure(Component component, Style parent, List<Integer> rows) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) for (int cp : text.content().codePoints().toArray()) {
            if (cp == '\n') { rows.add(0); continue; }
            Key font = style.font(); int advance;
            if (BookText.SPACE.equals(font)) advance = cp - 0xe100 - 512;
            else {
                assertNotNull(font);
                assertTrue(font.value().startsWith("compact/"), "存在未缩放的字形：" + font);
                String source = font.value().substring(8, font.value().lastIndexOf('/'));
                source = source.replaceFirst("/", ":");
                advance = source.equals("handbookcore:heading") ? 9
                        : source.equals("handbookcore:body") || source.equals("handbookcore:button") ? 8
                        : Integer.parseInt(metrics.getProperty(source + "/" + cp).split(",")[1]);
            }
            int index = rows.size()-1; rows.set(index, rows.get(index)+advance);
            assertTrue(rows.get(index) <= BookCanvas.DISPLAY_WIDTH, "中途超过客户端换行宽度");
        }
        for (Component child : component.children()) measure(child, style, rows);
    }
    @Test void compactCanvasFitsAndEveryNativeLineHasTheSameAdvance() {
        BookCanvas canvas = new BookCanvas();
        canvas.left(9, BookText.text("农".repeat(13)), 169);
        canvas.right(11, BookText.text("176 宽的逻辑栏"), 143);
        canvas.bookmark(8, 0, true, ClickEvent.runCommand("/handbook"), Component.text("章节"));
        canvas.hit(26, 420, 30, 3, ClickEvent.runCommand("/next"), Component.text("下一页"));
        List<Integer> rows = new ArrayList<>(List.of(0));
        measure(canvas.build(), Style.empty(), rows);
        assertEquals(293, BookCanvas.DISPLAY_WIDTH);
        assertEquals(18, rows.size());
        assertTrue(rows.stream().allMatch(width -> width == 293));
    }
    @Test void fractionalGlyphSpacingDoesNotAccumulateAndActionsSurvive() {
        Component source = BookText.text("农".repeat(13)).clickEvent(ClickEvent.openUrl("https://example.org"));
        var rendered = new BookScale(null).render(source, 62, 7);
        List<Integer> widths = new ArrayList<>(List.of(0)); measure(rendered.component(), Style.empty(), widths);
        assertEquals(95, widths.getFirst()); // round(231*.5625)-round(62*.5625)
        assertEquals(widths.getFirst(), rendered.advance());
        List<Component> glyphs = new ArrayList<>(); collect(rendered.component(), glyphs);
        assertEquals(13, glyphs.size());
        assertTrue(glyphs.stream().allMatch(c -> source.clickEvent().equals(c.clickEvent())));
        assertTrue(glyphs.stream().allMatch(c -> c.font().value().endsWith("/7")));
    }
    private void collect(Component component, List<Component> result) {
        if (component instanceof TextComponent t && t.content().contains("农")) result.add(t);
        for (var child : component.children()) collect(child, result);
    }
    @Test void rareUnicodeFallbackKeepsItsNativeWidthInsteadOfOverlapping() {
        String rare = new String(Character.toChars(0x20000));
        var runs = new BookText(null).wrap(BookText.text(rare.repeat(12)), 176);
        assertEquals(2, runs.size()); assertEquals(176, runs.getFirst().width());
        assertEquals(18, new BookScale(null).render(BookText.text(rare.repeat(2)), 0, 0).advance());
    }
    @Test void compactTextFontsShareBitmapsWithoutExpandingUnicodeFallback() throws Exception {
        String prefix = "/ce-pack/handbookcore/resourcepack/assets/handbookcore/";
        for (String role : List.of("body", "heading", "button")) for (int offset = 0; offset < 9; offset++) {
            try (var stream = getClass().getResourceAsStream(prefix + "font/compact/handbookcore/" + role + "/" + offset + ".json")) {
                var providers = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("providers");
                assertEquals("reference", providers.get(0).getAsJsonObject().get("type").getAsString());
                assertTrue(providers.get(0).getAsJsonObject().get("id").getAsString().startsWith("handbookcore:compact/shared/"));
                assertEquals(1, providers.size(), "紧凑副本不应展开整份 Unicode 回退");
            }
        }
        try (var stream = getClass().getResourceAsStream(prefix + "textures/font/glyphs.png")) {
            var png = javax.imageio.ImageIO.read(stream);
            assertEquals(2048, png.getWidth()); assertEquals(4000, png.getHeight());
        }
    }
    @Test void vanillaIconsShareAnAtlasInsteadOfAllocatingAUnicodeIndexForEveryIcon() throws Exception {
        String prefix = "/ce-pack/handbookcore/resourcepack/assets/handbookcore/";
        Properties icons = new Properties();
        try (var stream = getClass().getResourceAsStream("/vanilla-icons.properties")) { icons.load(stream); }
        Set<Integer> expected = new HashSet<>();
        for (var value : icons.values()) expected.add(Integer.parseInt(value.toString().split(",")[0]));
        assertTrue(expected.size() > 1000);
        for (String role : List.of("icons", "icons_small")) for (int offset = 0; offset < 9; offset++) {
            try (var stream = getClass().getResourceAsStream(prefix + "font/compact/handbookcore/" + role + "/" + offset + ".json")) {
                var providers = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("providers");
                assertEquals(1, providers.size(), "每个图标单独建 provider 会分配大量 Unicode 空表");
                var bitmap = providers.get(0).getAsJsonObject();
                assertEquals("handbookcore:font/vanilla-icons.png", bitmap.get("file").getAsString());
                Set<Integer> actual = new HashSet<>();
                bitmap.getAsJsonArray("chars").forEach(row -> row.getAsString().codePoints().filter(cp -> cp != 0).forEach(actual::add));
                assertEquals(expected, actual, "合并图集不能丢失可独立调用的原版图标");
            }
        }
    }
}
