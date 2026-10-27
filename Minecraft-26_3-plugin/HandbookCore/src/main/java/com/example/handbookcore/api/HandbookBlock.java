package com.example.handbookcore.api;

import java.util.Objects;
import org.bukkit.NamespacedKey;

/** 文章内容块；分页、换行、CE 解析与交互均由核心处理。 */
public sealed interface HandbookBlock {
    record Text(String miniMessage) implements HandbookBlock {
        public Text { Objects.requireNonNull(miniMessage, "miniMessage"); }
    }
    /** width/height 是图片的实际 GUI 像素尺寸，应与 CE image 配置匹配。 */
    record Image(String imageId, int width, int height, String altText) implements HandbookBlock {
        public Image {
            if (Objects.requireNonNull(imageId, "imageId").isBlank()) throw new IllegalArgumentException("图片 id 不能为空");
            if (width < 1 || width > 176 || height < 1 || height > 144)
                throw new IllegalArgumentException("书页插图尺寸应为 1..176 × 1..144 GUI 像素");
            Objects.requireNonNull(altText, "altText");
        }
    }
    record Item(HandbookItem item, String caption) implements HandbookBlock {
        public Item { Objects.requireNonNull(item, "item"); Objects.requireNonNull(caption, "caption"); }
    }
    record Link(String label, NamespacedKey entryId) implements HandbookBlock {
        public Link { Objects.requireNonNull(label, "label"); Objects.requireNonNull(entryId, "entryId"); }
    }
    /** 外部网页；由客户端确认后打开，仅接受 HTTP(S)。 */
    record Url(String label, String url) implements HandbookBlock {
        public Url {
            Objects.requireNonNull(label, "label"); Objects.requireNonNull(url, "url");
            java.net.URI target = java.net.URI.create(url);
            if (!("https".equalsIgnoreCase(target.getScheme()) || "http".equalsIgnoreCase(target.getScheme()))
                    || target.getHost() == null || target.getUserInfo() != null || url.length() > 2048)
                throw new IllegalArgumentException("外链必须是有效的 HTTP(S) 网页地址");
        }
    }
    /** 使用点击玩家自身权限执行；默认关闭书页，方便目标指令打开其他界面。 */
    record Command(String label, String command, boolean closeBeforeRun) implements HandbookBlock {
        public Command {
            Objects.requireNonNull(label, "label"); Objects.requireNonNull(command, "command");
            command = command.strip(); if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank() || command.length() > 256 || command.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("指令不能为空、包含控制字符或超过 256 字符");
        }
        public Command(String label, String command) { this(label, command, true); }
    }
    record Space(int lines) implements HandbookBlock {
        public Space { if (lines < 1 || lines > 8) throw new IllegalArgumentException("空白行应为 1..8"); }
    }
    static Text text(String miniMessage) { return new Text(miniMessage); }
}
