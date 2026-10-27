package com.example.handbookcore.api;

import java.util.List;

/** 一次发布或原子替换一个插件贡献的全部内容。 */
public record HandbookContribution(List<HandbookCategory> categories, List<HandbookEntry> entries) {
    public HandbookContribution { categories = List.copyOf(categories); entries = List.copyOf(entries); }
    public static HandbookContribution empty() { return new HandbookContribution(List.of(), List.of()); }
}
