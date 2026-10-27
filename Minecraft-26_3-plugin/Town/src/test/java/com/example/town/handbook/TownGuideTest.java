package com.example.town.handbook;

import com.example.handbookcore.api.HandbookBlock;
import com.example.handbookcore.api.HandbookEntry;
import com.example.handbookcore.internal.HandbookCatalog;
import com.example.handbookcore.render.BookCanvas;
import com.example.handbookcore.render.BookPaginator;
import com.example.handbookcore.render.BookText;
import com.example.town.TownConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TownGuideTest {
    private static TownConfig rules(int members, double founding, int free, double price, boolean diagonal) {
        return new TownConfig(members, 0, 2, 12, Pattern.compile(".+"),
                72 * 3600_000L, 30 * 60_000L, "", founding, 1, free, Set.of(), diagonal,
                256, price, "town:town_hall", Material.LODESTONE,
                new TownConfig.Protection(true, true, true, true, true, true, true, true, true, true),
                40, List.of(), 10, Map.of());
    }

    private static List<HandbookBlock> blocks(HandbookEntry entry) {
        return entry.pages().stream().flatMap(p -> p.blocks().stream()).toList();
    }

    private static String body(HandbookEntry entry) {
        return blocks(entry).stream().filter(HandbookBlock.Text.class::isInstance)
                .map(HandbookBlock.Text.class::cast).map(HandbookBlock.Text::miniMessage).collect(Collectors.joining());
    }

    @Test
    void completeContributionHasNoBrokenLinksAndIsSearchableWithPlayerPermission() {
        var guide = TownGuide.create(rules(1, 1000, 9, 50, false));
        HandbookCatalog catalog = new HandbookCatalog();
        catalog.replace("Town", guide);
        var snapshot = catalog.snapshot();
        assertEquals(14, snapshot.entries(TownGuide.CATEGORY, p -> true).size());
        assertTrue(snapshot.entries().containsKey(TownGuide.START));
        for (var entry : guide.entries()) {
            for (var block : blocks(entry)) {
                if (block instanceof HandbookBlock.Link link)
                    assertTrue(snapshot.entries().containsKey(link.entryId()), entry.id() + " → " + link.entryId());
            }
        }
        for (String query : List.of("圈地", "外镇 租地", "搬家", "未到账", "收件箱"))
            assertFalse(snapshot.search(query, p -> true).isEmpty(), query);
        assertTrue(snapshot.categories(p -> false).isEmpty());
        assertTrue(snapshot.entries(TownGuide.CATEGORY, p -> false).isEmpty());
    }

    @Test
    void replacingGuideRefreshesLiveRulesAndKeepsStableEntryIds() {
        HandbookCatalog catalog = new HandbookCatalog();
        catalog.replace("Town", TownGuide.create(rules(1, 1000, 9, 50, false)));
        var originalIds = catalog.snapshot().entries().keySet();
        catalog.replace("Town", TownGuide.create(rules(4, 2345.67, 13, 78.9, true)));
        assertEquals(originalIds, catalog.snapshot().entries().keySet());
        String content = catalog.snapshot().entries().values().stream().map(e -> body(e.value())).collect(Collectors.joining());
        for (String value : List.of("至少需要 4 人", "2345.67", "总额度为 13 块", "其余 12 块", "78.9", "斜角相邻也算相连"))
            assertTrue(content.contains(value), value);
        assertFalse(content.contains("只认上下左右相邻"));
    }

    @Test
    void actualBookPaginationPreservesAllParagraphsAndActionsWithinThePage() {
        var guide = TownGuide.create(rules(1, 1000, 9, 50, false));
        var paginator = new BookPaginator(new BookText(null), null);
        var serializer = PlainTextComponentSerializer.plainText();
        for (var entry : guide.entries()) {
            var papers = paginator.paginate(entry);
            String rendered = papers.stream().flatMap(p -> p.lines().stream())
                    .map(l -> serializer.serialize(l.text())).collect(Collectors.joining()).replaceAll("\\s+", "");
            for (var paper : papers) {
                assertTrue(paper.lines().size() <= BookCanvas.CONTENT_ROWS, entry.id().toString());
                assertTrue(paper.lines().stream().allMatch(l -> l.width() <= BookCanvas.COLUMN_WIDTH), entry.id().toString());
            }
            for (var block : blocks(entry)) {
                if (block instanceof HandbookBlock.Text text)
                    assertTrue(rendered.contains(text.miniMessage().replaceAll("\\s+", "")), entry.id().toString());
                if (block instanceof HandbookBlock.Link || block instanceof HandbookBlock.Command)
                    assertTrue(papers.stream().flatMap(p -> p.lines().stream()).anyMatch(l -> block.equals(l.action())), entry.id().toString());
            }
        }
    }
}
