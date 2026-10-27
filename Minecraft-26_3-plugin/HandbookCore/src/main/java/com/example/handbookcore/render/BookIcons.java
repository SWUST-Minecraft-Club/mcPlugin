package com.example.handbookcore.render;

import com.example.handbookcore.api.HandbookIcon;
import com.example.handbookcore.hook.CraftEngineBridge;
import java.util.Properties;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/** 原版图标引用客户端自带材质，CE 图片使用自己的字体与真实 advance。 */
public final class BookIcons {
    public record Icon(Component component, int width, int height) { }
    private final Properties vanilla = new Properties();
    private final CraftEngineBridge ce;
    public BookIcons(CraftEngineBridge ce) {
        this.ce = ce;
        try (var stream = BookIcons.class.getResourceAsStream("/vanilla-icons.properties")) {
            if (stream == null) throw new IllegalStateException("缺少原版图标索引");
            vanilla.load(stream);
        } catch (java.io.IOException error) { throw new IllegalStateException("读取图标索引失败", error); }
    }
    public Icon resolve(HandbookIcon ref) {
        return resolve(ref, false);
    }
    public Icon resolveSmall(HandbookIcon ref) { return resolve(ref, true); }
    private Icon resolve(HandbookIcon ref, boolean small) {
        String imageId = small ? ref.bookmarkImageId() : ref.imageId();
        if (!imageId.isEmpty()) {
            var image = ce.image(imageId, 0, 0);
            if (image.isPresent()) {
                Icon result = ceIcon(image.get());
                if (result != null && result.width() <= (small ? 24 : 76) && result.height() <= (small ? 20 : 40)) return result;
            }
        }
        String[] data = vanilla.getProperty(ref.fallback().getKey().value(), vanilla.getProperty("book")).split(",");
        int advance = Integer.parseInt(data[small ? 3 : 1]), bearing = Integer.parseInt(data[small ? 4 : 2]);
        Component glyph = Component.text(new String(Character.toChars(Integer.parseInt(data[0]))))
                .font(Key.key(small ? "handbookcore:icons_small" : "handbookcore:icons")).color(NamedTextColor.WHITE);
        return new Icon(BookText.shift(-bearing).append(glyph), advance - bearing, small ? 16 : 32);
    }
    private Icon ceIcon(Component value) {
        if (value instanceof TextComponent text && !text.content().isEmpty() && value.font() != null) {
            var metrics = ce.metrics(value.font(), text.content().codePointAt(0));
            if (metrics.isPresent()) return new Icon(BookText.shift(-metrics.get().bearing()).append(value.color(NamedTextColor.WHITE)),
                    metrics.get().advance()-metrics.get().bearing(), metrics.get().height());
        }
        for (Component child : value.children()) { Icon result = ceIcon(child); if (result != null) return result; }
        return null;
    }
}
