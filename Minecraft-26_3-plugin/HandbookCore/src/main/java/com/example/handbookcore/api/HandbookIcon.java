package com.example.handbookcore.api;

import java.util.Objects;
import org.bukkit.Material;

/** 分类图标；imageId 是 CE images 的 namespaced id，空字符串表示使用内置图标。 */
public record HandbookIcon(Material fallback, String imageId, String bookmarkImageId) {
    public HandbookIcon {
        Objects.requireNonNull(fallback, "fallback");
        imageId = Objects.requireNonNull(imageId, "imageId");
        bookmarkImageId = Objects.requireNonNull(bookmarkImageId, "bookmarkImageId");
        if (fallback == Material.AIR || fallback == Material.CAVE_AIR || fallback == Material.VOID_AIR)
            throw new IllegalArgumentException("图标不能是空气");
    }
    public HandbookIcon(Material fallback, String imageId) { this(fallback, imageId, imageId); }
    public static HandbookIcon vanilla(Material material) { return new HandbookIcon(material, ""); }
    public static HandbookIcon image(String id, Material fallback) { return new HandbookIcon(fallback, id); }
    /** 目录图片与书签小图分别指定；缺失或超大时使用原版图标。 */
    public static HandbookIcon image(String id, String bookmarkId, Material fallback) { return new HandbookIcon(fallback, id, bookmarkId); }
}
