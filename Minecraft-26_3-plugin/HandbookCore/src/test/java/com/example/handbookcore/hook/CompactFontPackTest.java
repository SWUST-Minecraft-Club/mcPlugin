package com.example.handbookcore.hook;

import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CompactFontPackTest {
    @TempDir Path root;
    public static final class Bitmap {
        public String font() { return "other:guide"; }
        public String file() { return "other:maps/large.png"; }
        public int height() { return 144; }
        public int ascent() { return 7; }
        public int[][] codepointGrid() { return new int[][]{{0xf0000, 0xe001}}; }
    }
    @Test void thirdPartyImagesKeepTheirTexturesAndGridAndAreRebuiltOnReload() throws Exception {
        CompactFontPack.write(root, List.of(new Bitmap()));
        Path fonts = root.resolve("assets/handbookcore/font/compact/other/guide");
        try (var paths = Files.list(fonts)) { assertEquals(9, paths.count()); }
        var provider = JsonParser.parseString(Files.readString(fonts.resolve("8.json"))).getAsJsonObject()
                .getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals("other:maps/large.png", provider.get("file").getAsString());
        assertEquals(81, provider.get("height").getAsInt());
        assertEquals(-1, provider.get("ascent").getAsInt());
        assertArrayEquals(new int[]{0xf0000,0xe001}, provider.getAsJsonArray("chars").get(0).getAsString().codePoints().toArray());
        CompactFontPack.write(root, List.of());
        assertFalse(Files.exists(fonts));
    }
}
