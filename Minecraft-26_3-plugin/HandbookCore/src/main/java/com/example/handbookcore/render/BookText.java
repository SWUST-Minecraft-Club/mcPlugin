package com.example.handbookcore.render;

import com.example.handbookcore.hook.CraftEngineBridge;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** 使用包内定宽 bitmap 字体，让服务器可以准确排双栏；CE 图片保留其字体。 */
public final class BookText {
    public static final Key BODY = Key.key("handbookcore:body");
    public static final Key HEADING_FONT = Key.key("handbookcore:heading");
    public static final Key BUTTON_FONT = Key.key("handbookcore:button");
    public static final Key SPACE = Key.key("handbookcore:space");
    public static final TextColor INK = TextColor.color(0x44321f);
    public static final TextColor HEADING = TextColor.color(0x79521e);
    private static final BitSet GLYPHS = glyphs();
    static boolean supported(int cp) { return GLYPHS.get(cp); }
    private static BitSet glyphs() {
        try (var stream = BookText.class.getResourceAsStream("/book-font.bin")) {
            return stream == null ? new BitSet() : BitSet.valueOf(stream.readAllBytes());
        } catch (java.io.IOException error) { throw new IllegalStateException("读取书页字体指标失败", error); }
    }
    private final CraftEngineBridge ce;
    public BookText(CraftEngineBridge ce) { this.ce = ce; }
    public Component parse(String text) {
        try {
            return MiniMessage.miniMessage().deserialize(text, TagResolver.resolver("image", (args, ctx) -> {
                String namespace = args.popOr("缺少图片命名空间").value();
                String id = args.popOr("缺少图片 id").value();
                int row = args.hasNext() ? args.pop().asInt().orElse(0) : 0;
                int column = args.hasNext() ? args.pop().asInt().orElse(0) : 0;
                return Tag.inserting(ce.image(namespace + ":" + id, row, column).orElse(Component.text("[图片]")));
            }), TagResolver.resolver("shift", (args, ctx) -> Tag.inserting(shift(args.popOr("缺少偏移量").asInt().orElse(0)))));
        } catch (RuntimeException error) { return Component.text(text); }
    }
    public static Component shift(int pixels) {
        Component result = Component.empty();
        while (pixels != 0) {
            int step = Math.clamp(pixels, -512, 512);
            result = result.append(Component.text(String.valueOf((char) (0xe100 + step + 512))).font(SPACE)
                    .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false));
            pixels -= step;
        }
        return result;
    }
    public static Component text(String value) { return Component.text(value).font(BODY).color(INK); }
    public record Run(Component component, int width, int height) {
        public Run(Component component, int width) { this(component, width, 12); }
    }
    /** 单行控件按真实字宽截断，并用省略号保留明确的溢出提示。 */
    public Run ellipsize(Component component, int maxWidth) {
        List<Run> glyphs = new ArrayList<>(); flatten(component, Style.empty(), glyphs);
        int total = glyphs.stream().filter(g -> g.width() != Integer.MIN_VALUE).mapToInt(Run::width).sum();
        boolean truncate = total > maxWidth || glyphs.stream().anyMatch(g -> g.width() == Integer.MIN_VALUE);
        if (!truncate) return new Run(component.font(component.font() == null ? BODY : component.font()), total);
        var dots = wrap(Component.text("…").style(component.style()), maxWidth).getFirst();
        Component line = Component.empty(); int width = 0;
        for (Run glyph : glyphs) {
            if (glyph.width() == Integer.MIN_VALUE || width + glyph.width() + dots.width() > maxWidth) break;
            line = line.append(glyph.component()); width += glyph.width();
        }
        return new Run(line.append(dots.component()), width + dots.width());
    }
    public List<Run> wrap(Component component, int maxWidth) {
        List<Run> result = new ArrayList<>(); List<Run> glyphs = new ArrayList<>(); flatten(component, Style.empty(), glyphs);
        Component line = Component.empty(); int width = 0, height = 12;
        for (Run glyph : glyphs) {
            if (glyph.width() == Integer.MIN_VALUE) { result.add(new Run(line, width, height)); line = Component.empty(); width = 0; height = 12; continue; }
            if (glyph.width() > maxWidth || glyph.height() > BookCanvas.CONTENT_ROWS * BookCanvas.LINE_HEIGHT) {
                if (width > 0) { result.add(new Run(line, width, height)); line = Component.empty(); width = 0; height = 12; }
                result.add(new Run(text("[插图超出书页]"), 117)); continue;
            }
            if (width > 0 && width + glyph.width() > maxWidth) { result.add(new Run(line, width, height)); line = Component.empty(); width = 0; height = 12; }
            line = line.append(glyph.component()); width += glyph.width(); height = Math.max(height, glyph.height());
        }
        result.add(new Run(line, width, height)); return result;
    }
    private void flatten(Component component, Style inherited, List<Run> output) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        // 书页正文采用定宽；特殊字形通过独立 Image 块提供显式尺寸。
        if (component instanceof TextComponent text) {
            for (int cp : text.content().codePoints().toArray()) {
                if (cp == '\n') { output.add(new Run(Component.empty(), Integer.MIN_VALUE)); continue; }
                if (cp == '\r') continue;
                boolean custom = style.font() != null && !style.font().equals(BODY);
                Style target = custom ? style : style.font(BODY);
                int width = (GLYPHS.get(cp) ? (HEADING_FONT.equals(style.font()) ? 15 : 13) : BookScale.FALLBACK_WIDTH)
                        + (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE ? 1 : 0);
                if (custom && style.font().equals(SPACE)) width = cp - 0xe100 - 512;
                int height = 12;
                if (custom && !style.font().equals(SPACE) && !style.font().equals(HEADING_FONT) && ce != null) {
                    var metrics = ce.metrics(style.font(), cp);
                    if (metrics.isPresent()) { width = metrics.get().advance(); height = metrics.get().height(); }
                }
                output.add(new Run(Component.text(new String(Character.toChars(cp))).style(target), width, height));
            }
        } else {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component.children(List.of()));
            plain.codePoints().forEach(cp -> output.add(new Run(Component.text(new String(Character.toChars(cp))).style(style.font(BODY)), 9)));
        }
        for (Component child : component.children()) flatten(child, style, output);
    }
}
