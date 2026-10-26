package com.example.town.arcmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.town.model.ChunkKey;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TownMapRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void fileKeyIsVersionedAndSanitized() {
        assertEquals("v3_my_world_-2_3", TownMapRenderer.fileKey(new ChunkKey("My World", -2, 3)));
        assertEquals("v3_world_4_5", TownMapRenderer.fileKey(new ChunkKey("   ", 4, 5)));
        assertFalse(TownMapRenderer.fileKey(new ChunkKey("世界", 1, 2)).contains("世界"));
    }

    @Test
    void readyRequiresBothIndexedEntryAndFile() throws Exception {
        TownMapRenderer renderer = new TownMapRenderer(null, tempDir.resolve("ArcMenu"));
        String key = "v3_world_0_0";
        assertFalse(renderer.isReady(key));
        Files.createDirectories(renderer.fileFor(key).getParent());
        Files.writeString(renderer.fileFor(key), "not-an-image-but-state-test");
        assertFalse(renderer.isReady(key), "只有文件没有资源索引仍不能使用");

        Field indexedField = TownMapRenderer.class.getDeclaredField("indexed");
        indexedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> indexed = (Set<String>) indexedField.get(renderer);
        indexed.add(key);
        assertTrue(renderer.isReady(key));

        Files.delete(renderer.fileFor(key));
        assertFalse(renderer.isReady(key), "索引存在但文件被删除也不能使用");
    }

    @Test
    void finalPackMustContainMatchingFontProvidersAndImageBytes() throws Exception {
        Path arcDir = tempDir.resolve("ArcMenu");
        Path arc = arcDir.resolve("generated/arcmenu-resourcepack.zip");
        Files.createDirectories(arc.getParent());
        String font = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"arcmenu:images/town/map/test.png\",\"chars\":[\"\\uE001\"],\"height\":8,\"ascent\":7}]}";
        pack(arc, font, "current-image");
        Path merged = tempDir.resolve("merged.zip");
        TownMapRenderer renderer = new TownMapRenderer(null, arcDir);
        var verify = TownMapRenderer.class.getDeclaredMethod("verifyMergedPack", Path.class, Set.class, long.class);
        verify.setAccessible(true);
        pack(merged, "{\"providers\":[]}", "current-image");
        assertFalse((boolean) verify.invoke(renderer, merged, Set.of("test"), 0L), "有 PNG 没有字体映射不能发布");
        pack(merged, font, "stale-image");
        assertFalse((boolean) verify.invoke(renderer, merged, Set.of("test"), 0L), "旧 PNG 不能被本次构建误认");
        pack(merged, font, "current-image");
        assertTrue((boolean) verify.invoke(renderer, merged, Set.of("test"), 0L));
    }

    private void pack(Path path, String font, String image) throws Exception {
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("assets/arcmenu/font/images.json"));
            out.write(font.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
            out.putNextEntry(new ZipEntry("assets/arcmenu/textures/images/town/map/test.png"));
            out.write(image.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
        }
    }
}
