package com.example.handbookcore.render;

import com.example.handbookcore.api.*;
import com.example.handbookcore.hook.CraftEngineBridge;
import java.util.*;
import net.kyori.adventure.text.Component;

/** 一个内容块可以占多行；图片整体放置，长文字自动续页。 */
public final class BookPaginator {
    public record Line(Component text, int width, HandbookBlock action, boolean centered) {
        public Line(Component text, int width, HandbookBlock action) { this(text, width, action, false); }
    }
    public record Paper(String title, List<Line> lines) {
        public Paper { lines = List.copyOf(lines); }
    }
    private final BookText text;
    private final CraftEngineBridge ce;
    public BookPaginator(BookText text, CraftEngineBridge ce) { this.text = text; this.ce = ce; }
    public List<Paper> paginate(HandbookEntry entry) {
        List<Paper> papers = new ArrayList<>();
        for (HandbookPage page : entry.pages()) {
            List<Line> lines = new ArrayList<>(); String title = page.title().isBlank() ? entry.title() : page.title();
            for (HandbookBlock block : page.blocks()) {
                List<List<Line>> groups = new ArrayList<>();
                switch (block) {
                    case HandbookBlock.Text value -> {
                        for (var line : text.wrap(text.parse(value.miniMessage()), BookCanvas.COLUMN_WIDTH)) {
                            List<Line> group = new ArrayList<>();
                            group.add(new Line(line.component(), line.width(), null));
                            for (int i = 1; i < Math.min(BookCanvas.CONTENT_ROWS, (line.height() + BookCanvas.LINE_HEIGHT - 1) / BookCanvas.LINE_HEIGHT); i++) group.add(blank());
                            groups.add(group);
                        }
                    }
                    case HandbookBlock.Image value -> {
                        var image = ce.image(value.imageId(), 0, 0);
                        if (image.isPresent()) {
                            List<Line> group = new ArrayList<>();
                            group.add(new Line(image.get().color(net.kyori.adventure.text.format.NamedTextColor.WHITE), value.width(), null, true));
                            for (int i = 1; i < (value.height() + BookCanvas.LINE_HEIGHT - 1) / BookCanvas.LINE_HEIGHT; i++) group.add(blank());
                            groups.add(group);
                        } else for (var line : text.wrap(BookText.text(value.altText()), BookCanvas.COLUMN_WIDTH))
                            groups.add(List.of(new Line(line.component(), line.width(), null)));
                    }
                    case HandbookBlock.Item value -> {
                        for (var line : text.wrap(BookText.text("◇ " + value.caption()), BookCanvas.COLUMN_WIDTH))
                            groups.add(List.of(new Line(line.component(), line.width(), value)));
                    }
                    case HandbookBlock.Link value -> {
                        for (var line : text.wrap(BookText.text("› " + value.label()), BookCanvas.COLUMN_WIDTH))
                            groups.add(List.of(new Line(line.component(), line.width(), value)));
                    }
                    case HandbookBlock.Url value -> {
                        for (var line : text.wrap(BookText.text("↗ " + value.label()), BookCanvas.COLUMN_WIDTH))
                            groups.add(List.of(new Line(line.component(), line.width(), value)));
                    }
                    case HandbookBlock.Command value -> {
                        for (var line : text.wrap(BookText.text("› " + value.label()), BookCanvas.COLUMN_WIDTH))
                            groups.add(List.of(new Line(line.component(), line.width(), value)));
                    }
                    case HandbookBlock.Space value -> { for (int i = 0; i < value.lines(); i++) groups.add(List.of(blank())); }
                }
                for (List<Line> group : groups) {
                    if (group.size() > BookCanvas.CONTENT_ROWS) throw new IllegalArgumentException("插图过高：" + entry.id());
                    if (!lines.isEmpty() && lines.size() + group.size() > BookCanvas.CONTENT_ROWS) {
                        papers.add(new Paper(title, lines)); lines = new ArrayList<>();
                    }
                    lines.addAll(group);
                }
            }
            papers.add(new Paper(title, lines));
        }
        return List.copyOf(papers);
    }
    private static Line blank() { return new Line(Component.empty(), 0, null); }
}
