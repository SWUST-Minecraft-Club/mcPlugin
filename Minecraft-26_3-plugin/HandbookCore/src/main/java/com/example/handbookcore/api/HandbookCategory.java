package com.example.handbookcore.api;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/** 不可变分类定义；permission 为空时对所有有 handbookcore.use 权限的玩家开放。 */
public record HandbookCategory(NamespacedKey id, String title, String description,
                               HandbookIcon icon, int order, String permission) {
    public HandbookCategory {
        Objects.requireNonNull(id, "id");
        if (Objects.requireNonNull(title, "title").isBlank()) throw new IllegalArgumentException("分类标题不能为空");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(permission, "permission");
    }
    public HandbookCategory(NamespacedKey id, String title, String description, Material icon, int order) {
        this(id, title, description, HandbookIcon.vanilla(icon), order, "");
    }
}
