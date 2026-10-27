package com.example.handbookcore.render;

import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;

/** 一条可点击的多行正文绘制整本书；每行净前进量相同，避免居中器把双栏错位。 */
public final class BookCanvas {
    public static final int WIDTH = 520, ROWS = 32, COLUMN_WIDTH = 176, CONTENT_ROWS = 8, LINE_HEIGHT = 18;
    public static final int LEFT = 62, RIGHT = 282, CONTENT_ROW = 9;
    public static final int BOOKMARK_X = 477, BOOKMARK_WIDTH = 42;
    // 将标题飘带左悬边与右侧动态书签都计入可见轮廓中心。
    private static final int ART_OFFSET_X = -7;
    private static final Properties PAPER_METRICS = paperMetrics();
    private static Properties paperMetrics() {
        Properties metrics = new Properties();
        try (var input = BookCanvas.class.getResourceAsStream("/book-metrics.properties")) {
            if (input == null) throw new IllegalStateException("缺少书页图集指标");
            metrics.load(input);
            return metrics;
        } catch (java.io.IOException error) { throw new IllegalStateException("读取书页图集指标失败", error); }
    }
    private record Cell(int x, Component value, int width) { }
    private final List<List<Cell>> rows = new ArrayList<>();
    public BookCanvas() { for (int i = 0; i < ROWS; i++) rows.add(new ArrayList<>()); }
    public void put(int row, int x, Component value, int width) {
        if (row < 0 || row >= ROWS || x < 0 || x + width > WIDTH) throw new IllegalArgumentException("内容超出书页");
        rows.get(row).add(new Cell(x + ART_OFFSET_X, value, width));
    }
    public void left(int row, Component value, int width) { put(row, LEFT, value, width); }
    public void right(int row, Component value, int width) { put(row, RIGHT, value, width); }
    public void centered(boolean right, int row, Component value, int width) {
        put(row, (right ? RIGHT : LEFT) + (COLUMN_WIDTH - width) / 2, value, width);
    }
    /** 透明命中区与纹理分离，允许底图按钮、图片和独立书签复用动作。 */
    public void hit(int row, int x, int width, int rowCount, net.kyori.adventure.text.event.ClickEvent click,
                    Component tooltip) {
        int index = List.of(30, 52, 82, 176, 42).indexOf(width);
        if (index < 0) throw new IllegalArgumentException("未注册的命中宽度：" + width);
        // 原生文本命中会跳过 space 字体；用几乎透明的 bitmap 字形覆盖真实按钮区域。
        Component area = Component.text(String.valueOf((char)(0xe220 + index))).font(Key.key("handbookcore:hit"))
                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE).clickEvent(click).hoverEvent(tooltip);
        for (int i = 0; i < rowCount; i++) put(row + i, x, area, width);
    }
    public void bookmark(int row, int color, boolean selected, net.kyori.adventure.text.event.ClickEvent click,
                         Component tooltip) {
        put(row, BOOKMARK_X, Component.text(String.valueOf((char)(0xe200 + (selected ? 4 : 0) + color)))
                .font(Key.key("handbookcore:bookmarks")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .clickEvent(click).hoverEvent(tooltip), BOOKMARK_WIDTH + 1);
        hit(row, BOOKMARK_X, BOOKMARK_WIDTH, 4, click, tooltip);
    }
    public Component build() {
        Component result = Component.empty().color(BookText.INK).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false);
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) result = result.append(Component.newline());
            int x = 0;
            if (row == 0) {
                // 分成 12 个不超过 256px 的单元，用 ascent 将三层放在同一个文本基线上。
                for (int tileRow = 0; tileRow < 3; tileRow++) for (int tileCol = 0; tileCol < 4; tileCol++) {
                    int target = 10 + ART_OFFSET_X + tileCol * 125;
                    int advance = Integer.parseInt(PAPER_METRICS.getProperty("paper." + tileRow + "." + tileCol));
                    result = result.append(BookText.shift(target - x)).append(Component.text(String.valueOf((char)(0xe000 + tileRow * 4 + tileCol)))
                            .font(Key.key("handbookcore:paper")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
                    x = target + advance;
                }
            }
            rows.get(row).sort(Comparator.comparingInt(Cell::x));
            for (Cell cell : rows.get(row)) {
                result = result.append(BookText.shift(cell.x() - x)).append(cell.value()); x = cell.x() + cell.width();
            }
            result = result.append(BookText.shift(WIDTH - x));
        }
        return result;
    }
}
