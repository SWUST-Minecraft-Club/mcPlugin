package com.example.handbookcore;

import com.example.handbookcore.api.*;
import com.example.handbookcore.internal.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HandbookCatalogTest {
    private static NamespacedKey key(String value) { return Objects.requireNonNull(NamespacedKey.fromString(value)); }
    private static HandbookCategory category(String id, String permission) {
        return new HandbookCategory(key(id), "分类", "说明", HandbookIcon.vanilla(Material.BOOK), 0, permission);
    }
    private static HandbookEntry entry(String id, String category) {
        return new HandbookEntry(key(id), key(category), "农耕", "播种方法", List.of("种子"), 0, "",
                List.of(HandbookPage.text("浇水", "用水壶浇灌作物")));
    }
    @Test void failedReplacementLeavesPreviousSnapshotAndOtherOwnersIntact() {
        var c = new HandbookCatalog(); c.category("A", category("a:main", "")); c.entry("A", entry("a:guide", "a:main"));
        c.category("B", category("b:main", "")); var before = c.snapshot();
        assertThrows(IllegalArgumentException.class, () -> c.replace("A", new HandbookContribution(
                List.of(category("b:main", "")), List.of())));
        assertSame(before, c.snapshot());
        c.replace("A", new HandbookContribution(List.of(category("a:new", "")), List.of(entry("a:new-guide", "a:new"))));
        assertFalse(c.snapshot().entries().containsKey(key("a:guide")));
        assertTrue(c.snapshot().categories().containsKey(key("b:main")));
    }
    @Test void rejectsDuplicatesAndCrossOwnerRemoval() {
        var c = new HandbookCatalog(); c.category("A", category("a:main", ""));
        assertThrows(IllegalArgumentException.class, () -> c.category("B", category("a:main", "")));
        assertThrows(IllegalArgumentException.class, () -> c.unregister("B", key("a:main")));
        assertThrows(IllegalArgumentException.class, () -> c.entry("B", entry("b:entry", "b:missing")));
    }
    @Test void revokedCategoryHidesCrossPluginArticlesAndRestoringItRevealsThem() {
        var c = new HandbookCatalog(); c.category("A", category("a:main", "")); c.entry("B", entry("b:entry", "a:main"));
        c.removeOwner("A"); assertTrue(c.snapshot().entries(null, p -> true).isEmpty());
        c.category("A", category("a:main", "")); assertEquals(1, c.snapshot().entries(null, p -> true).size());
    }
    @Test void searchChecksCategoryPermissionAndMatchesBodyKeywordsAndMultipleTerms() {
        var c = new HandbookCatalog(); c.category("A", category("a:main", "secret")); c.entry("A", entry("a:guide", "a:main"));
        assertTrue(c.snapshot().search("种子 浇灌", p -> p.isEmpty()).isEmpty());
        assertEquals(1, c.snapshot().search("种子 浇灌", p -> true).size());
        assertTrue(c.snapshot().search("不匹配", p -> true).isEmpty());
    }
    @Test void readersObserveImmutableSnapshots() {
        var c = new HandbookCatalog(); var before = c.snapshot(); c.category("A", category("a:main", ""));
        assertTrue(before.categories().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> c.snapshot().categories().clear());
    }
    @Test void manyIndependentProvidersRemainSearchableAndCleanUpIndividually() {
        var catalog = new HandbookCatalog();
        for (int owner=0; owner<12; owner++) {
            List<HandbookCategory> categories=new ArrayList<>(); List<HandbookEntry> entries=new ArrayList<>();
            for (int group=0; group<3; group++) {
                String id="provider"+owner+":category"+group;
                categories.add(category(id,""));
                for (int article=0; article<25; article++) entries.add(entry("provider"+owner+":entry"+group+"-"+article,id));
            }
            catalog.replace("Provider"+owner,new HandbookContribution(categories,entries));
        }
        assertEquals(36,catalog.snapshot().categories().size());
        assertEquals(900,catalog.snapshot().search("种子",p -> true).size());
        catalog.removeOwner("Provider7");
        assertEquals(33,catalog.snapshot().categories().size());
        assertEquals(825,catalog.snapshot().search("种子",p -> true).size());
        assertTrue(catalog.snapshot().entries().containsKey(key("provider8:entry2-24")));
    }
    @Test void invalidBatchCannotPartiallyPublish() {
        var c = new HandbookCatalog();
        assertThrows(IllegalArgumentException.class, () -> c.replace("A", new HandbookContribution(
                List.of(category("a:main", "")), List.of(entry("a:good", "a:main"), entry("a:bad", "a:missing")))));
        assertTrue(c.snapshot().categories().isEmpty()); assertTrue(c.snapshot().entries().isEmpty());
    }
    @Test void invalidBlockFailsWithFilename(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("blocks.yml"), """
                entries:
                  - id: test:entry
                    category: test:category
                    title: 测试
                    pages:
                      - blocks:
                          - type: unknown
                """);
        Exception error = assertThrows(Exception.class, () -> new ContentLoader().load(dir));
        assertTrue(error.getMessage().contains("未知内容块"));
        assertTrue(error.getMessage().contains("blocks.yml"));
    }
    @Test void malformedYamlFailsWithFilename(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("broken.yml"), "entries:\n  - id: foo:bar\n    title: Oops\n");
        Exception error = assertThrows(Exception.class, () -> new ContentLoader().load(dir));
        assertTrue(error.getMessage().contains("broken.yml"));
    }
}
