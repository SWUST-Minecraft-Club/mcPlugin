package com.example.handbookcore.api;

import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;

public record HandbookEntry(NamespacedKey id, NamespacedKey categoryId, String title,
                            String summary, List<String> keywords, int order,
                            String permission, List<HandbookPage> pages) {
    public HandbookEntry {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(categoryId, "categoryId");
        if (Objects.requireNonNull(title, "title").isBlank()) throw new IllegalArgumentException("条目标题不能为空");
        Objects.requireNonNull(summary, "summary"); Objects.requireNonNull(permission, "permission");
        keywords = List.copyOf(keywords); pages = List.copyOf(pages);
        if (pages.isEmpty() || pages.size() > 128) throw new IllegalArgumentException("条目应有 1..128 个逻辑页");
    }
    public HandbookEntry(NamespacedKey id, NamespacedKey categoryId, String title, List<HandbookPage> pages) {
        this(id, categoryId, title, "", List.of(), 0, "", pages);
    }
}
