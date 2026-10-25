package com.example.town.arcmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.town.model.ChunkKey;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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
}
