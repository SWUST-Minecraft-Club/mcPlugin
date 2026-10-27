package com.example.handbookcore.render;

import com.example.handbookcore.hook.CraftEngineBridge;
import java.util.Properties;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/** 贡献 API 保留逻辑坐标；仅在输出时缩放字体、图片与坐标。 */
public final class BookScale {
    public static final float FACTOR = .5625f;
    public static final int PAPER_TILE_WIDTH = 70;
    public static final int FALLBACK_WIDTH = 16; // 原生 9px 回退字符保留可读尺寸，对应 16px 逻辑宽度。
    private static final Properties METRICS = metrics();
    private final CraftEngineBridge ce;
    public BookScale(CraftEngineBridge ce) { this.ce = ce; }
    public static int pixels(int logical) { return Math.round(logical * FACTOR); }
    public static Key font(Key original, int offset) {
        return Key.key("handbookcore", "compact/" + original.namespace() + "/" + original.value() + "/" + offset);
    }
    private static Properties metrics() {
        Properties result = new Properties();
        try (var stream = BookScale.class.getResourceAsStream("/compact-glyphs.properties")) {
            if (stream == null) throw new IllegalStateException("缺少缩放字形指标");
            result.load(stream); return result;
        } catch (java.io.IOException error) { throw new IllegalStateException("读取缩放字形指标失败", error); }
    }
    private record Glyph(int original, int compact) { }
    private Glyph glyph(Key font, int cp) {
        if ((font.equals(BookText.BODY) || font.equals(BookText.HEADING_FONT) || font.equals(BookText.BUTTON_FONT))
                && BookText.supported(cp)) return font.equals(BookText.HEADING_FONT) ? new Glyph(15, 9) : new Glyph(13, 8);
        String value = METRICS.getProperty(font.asString() + "/" + cp);
        if (value != null) {
            String[] widths = value.split(",");
            return new Glyph(Integer.parseInt(widths[0]), Integer.parseInt(widths[1]));
        }
        if (ce != null) {
            var original = ce.metrics(font, cp); var compact = ce.metrics(font, cp, true);
            if (original.isPresent() && compact.isPresent()) return new Glyph(original.get().advance(), compact.get().advance());
        }
        return null;
    }
    public record Rendered(Component component, int advance) { }
    public Rendered render(Component value, int logicalX, int offset) {
        Pen pen = new Pen(logicalX, offset);
        append(value, Style.empty().font(BookText.BODY), pen);
        return new Rendered(pen.result, pixels(pen.logicalX) - pixels(logicalX));
    }
    private static final class Pen {
        int logicalX; final int offset; Component result = Component.empty();
        Pen(int logicalX, int offset) { this.logicalX = logicalX; this.offset = offset; }
    }
    private void append(Component value, Style inherited, Pen pen) {
        Style style = value.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        Key source = style.font() == null ? BookText.BODY : style.font();
        if (value instanceof TextComponent text) for (int cp : text.content().codePoints().toArray()) {
            if (source.equals(BookText.SPACE)) {
                int next = pen.logicalX + cp - 0xe100 - 512;
                pen.result = pen.result.append(BookText.shift(pixels(next) - pixels(pen.logicalX)));
                pen.logicalX = next; continue;
            }
            Glyph metrics = glyph(source, cp);
            int bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE ? 1 : 0;
            boolean bodyFont = source.equals(BookText.BODY) || source.equals(BookText.HEADING_FONT) || source.equals(BookText.BUTTON_FONT);
            int original = (metrics == null ? (bodyFont ? FALLBACK_WIDTH : 9) : metrics.original()) + bold;
            int actual = (metrics == null ? 9 : metrics.compact()) + bold;
            Style target = metrics == null ? (bodyFont ? style.font(Key.key("handbookcore:fallback")) : style)
                    : style.font(font(source, pen.offset));
            pen.result = pen.result.append(Component.text(new String(Character.toChars(cp))).style(target));
            int next = pen.logicalX + original;
            pen.result = pen.result.append(BookText.shift(pixels(next) - pixels(pen.logicalX) - actual));
            pen.logicalX = next;
        }
        for (Component child : value.children()) append(child, style, pen);
    }
}
