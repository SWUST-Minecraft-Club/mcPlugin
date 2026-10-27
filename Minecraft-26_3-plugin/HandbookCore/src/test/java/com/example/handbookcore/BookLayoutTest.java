package com.example.handbookcore;

import com.example.handbookcore.api.*;
import com.example.handbookcore.render.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookLayoutTest {
    private HandbookEntry entry(HandbookBlock block) {
        return new HandbookEntry(Objects.requireNonNull(NamespacedKey.fromString("test:article")),
                Objects.requireNonNull(NamespacedKey.fromString("test:category")), "测试", List.of(new HandbookPage("标题", List.of(block))));
    }
    @Test void longBodyPaginatesWithoutLosingCharacters() {
        BookText text = new BookText(null);
        var pages = new BookPaginator(text, null).paginate(entry(HandbookBlock.text("农".repeat(600))));
        assertEquals(6, pages.size());
        assertTrue(pages.stream().allMatch(p -> p.lines().size() <= BookCanvas.CONTENT_ROWS));
        String restored = pages.stream().flatMap(p -> p.lines().stream())
                .map(l -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(l.text()))
                .reduce("", String::concat);
        assertEquals("农".repeat(600), restored);
    }
    @Test void explicitNewlinesAndStyleArePreserved() {
        BookText text = new BookText(null); var runs = text.wrap(text.parse("<red>播种\n浇水</red>"), 135);
        assertEquals(2, runs.size()); assertEquals(26, runs.getFirst().width());
        assertTrue(runs.getFirst().component().toString().contains("red"));
    }
    @Test void canvasRejectsOffPageContent() {
        assertThrows(IllegalArgumentException.class, () -> new BookCanvas().put(1, BookCanvas.WIDTH-5, Component.text("x"), 20));
        assertThrows(IllegalArgumentException.class, () -> new BookCanvas().put(BookCanvas.ROWS, 1, Component.text("x"), 9));
    }
    @Test void longControlsUseEllipsisAndRetainTheirFullWidthLimit() {
        BookText text = new BookText(null);
        var run = text.ellipsize(text.parse("<green>很长的玩法插件注册分类名称</green>"), 78);
        assertTrue(run.width() <= 78);
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(run.component()).endsWith("…"));
    }
    @Test void imageCannotOverflowOnePaper() {
        assertThrows(IllegalArgumentException.class, () -> new HandbookBlock.Image("a:b", BookCanvas.COLUMN_WIDTH+1, 50, "说明"));
        assertThrows(IllegalArgumentException.class, () -> new HandbookBlock.Image("a:b", 20, BookCanvas.LINE_HEIGHT*BookCanvas.CONTENT_ROWS+1, "说明"));
    }
    @Test void externalActionsValidateTheirTargetsAndRemainClickableAfterPagination() {
        assertThrows(IllegalArgumentException.class, () -> new HandbookBlock.Url("危险", "file:///tmp/test"));
        assertThrows(IllegalArgumentException.class, () -> new HandbookBlock.Command("坏指令", "help\nstop"));
        assertEquals("help", new HandbookBlock.Command("帮助", "/help").command());
        var url = new HandbookBlock.Url("服务器官网", "https://example.org/guide");
        var lines = new BookPaginator(new BookText(null), null).paginate(entry(url)).getFirst().lines();
        assertTrue(lines.stream().allMatch(line -> line.action() == url));
    }
}
