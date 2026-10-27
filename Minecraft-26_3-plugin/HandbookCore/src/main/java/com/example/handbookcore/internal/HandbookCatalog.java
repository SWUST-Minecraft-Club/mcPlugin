package com.example.handbookcore.internal;

import com.example.handbookcore.api.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;

/** 写入采用同步复制，读者只持有不可变快照；不同 Folia 区域不会看到半批配置。 */
public final class HandbookCatalog {
    public record Owned<T>(String owner, T value) { }
    public record Snapshot(long revision, Map<NamespacedKey, Owned<HandbookCategory>> categories,
                           Map<NamespacedKey, Owned<HandbookEntry>> entries) {
        public Snapshot { categories = Map.copyOf(categories); entries = Map.copyOf(entries); }
        public boolean visible(HandbookEntry entry, Predicate<String> permitted) {
            Owned<HandbookCategory> category = categories.get(entry.categoryId());
            return category != null && permitted.test(category.value().permission()) && permitted.test(entry.permission());
        }
        public List<HandbookCategory> categories(Predicate<String> permitted) {
            return categories.values().stream().map(Owned::value).filter(c -> permitted.test(c.permission()))
                    .sorted(Comparator.comparingInt(HandbookCategory::order).thenComparing(c -> c.id().toString())).toList();
        }
        public List<HandbookEntry> entries(NamespacedKey category, Predicate<String> permitted) {
            return entries.values().stream().map(Owned::value).filter(e -> category == null || category.equals(e.categoryId()))
                    .filter(e -> visible(e, permitted)).sorted(ENTRY_ORDER).toList();
        }
        public List<HandbookEntry> search(String query, Predicate<String> permitted) {
            String normalized = plain(query).strip().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) return List.of();
            String[] terms = normalized.split("\\s+");
            return entries(null, permitted).stream().filter(e -> {
                String haystack = searchable(e);
                return Arrays.stream(terms).allMatch(haystack::contains);
            }).toList();
        }
    }
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Comparator<HandbookEntry> ENTRY_ORDER = Comparator.comparingInt(HandbookEntry::order)
            .thenComparing(e -> e.id().toString());
    private volatile Snapshot snapshot = new Snapshot(0, Map.of(), Map.of());
    public Snapshot snapshot() { return snapshot; }
    public synchronized void category(String owner, HandbookCategory category) {
        var categories = new HashMap<>(snapshot.categories());
        requireNew(category.id()); categories.put(category.id(), new Owned<>(owner, category));
        publish(categories, snapshot.entries());
    }
    public synchronized void entry(String owner, HandbookEntry entry) {
        requireNew(entry.id());
        if (!snapshot.categories().containsKey(entry.categoryId())) throw new IllegalArgumentException("分类不存在：" + entry.categoryId());
        var entries = new HashMap<>(snapshot.entries()); entries.put(entry.id(), new Owned<>(owner, entry));
        publish(snapshot.categories(), entries);
    }
    public synchronized void replace(String owner, HandbookContribution contribution) {
        var categories = new HashMap<>(snapshot.categories()); var entries = new HashMap<>(snapshot.entries());
        categories.values().removeIf(c -> c.owner().equals(owner)); entries.values().removeIf(e -> e.owner().equals(owner));
        Set<NamespacedKey> ids = new HashSet<>(categories.keySet()); ids.addAll(entries.keySet());
        for (var category : contribution.categories()) {
            if (!ids.add(category.id())) throw new IllegalArgumentException("手册 id 重复：" + category.id());
            categories.put(category.id(), new Owned<>(owner, category));
        }
        for (var entry : contribution.entries()) {
            if (!ids.add(entry.id())) throw new IllegalArgumentException("手册 id 重复：" + entry.id());
            if (!categories.containsKey(entry.categoryId())) throw new IllegalArgumentException("分类不存在：" + entry.categoryId());
            entries.put(entry.id(), new Owned<>(owner, entry));
        }
        publish(categories, entries);
    }
    public synchronized void unregister(String owner, NamespacedKey id) {
        var categories = new HashMap<>(snapshot.categories()); var entries = new HashMap<>(snapshot.entries());
        Owned<?> value = categories.containsKey(id) ? categories.get(id) : entries.get(id);
        if (value == null) return;
        if (!value.owner().equals(owner)) throw new IllegalArgumentException("不能注销其他插件的内容：" + id);
        categories.remove(id); entries.remove(id); publish(categories, entries);
    }
    public void removeOwner(String owner) { replace(owner, HandbookContribution.empty()); }
    private void requireNew(NamespacedKey id) {
        if (snapshot.categories().containsKey(id) || snapshot.entries().containsKey(id)) throw new IllegalArgumentException("手册 id 重复：" + id);
    }
    private void publish(Map<NamespacedKey, Owned<HandbookCategory>> categories, Map<NamespacedKey, Owned<HandbookEntry>> entries) {
        snapshot = new Snapshot(snapshot.revision() + 1, categories, entries);
    }
    public static String plain(String value) { return TAGS.matcher(value).replaceAll(""); }
    private static String searchable(HandbookEntry e) {
        StringBuilder text = new StringBuilder(e.id() + " " + e.title() + " " + e.summary() + " " + String.join(" ", e.keywords()));
        for (var page : e.pages()) {
            text.append(' ').append(page.title());
            for (var block : page.blocks()) {
                if (block instanceof HandbookBlock.Text t) text.append(' ').append(t.miniMessage());
                if (block instanceof HandbookBlock.Image i) text.append(' ').append(i.altText());
                if (block instanceof HandbookBlock.Item i) text.append(' ').append(i.caption());
                if (block instanceof HandbookBlock.Link l) text.append(' ').append(l.label());
                if (block instanceof HandbookBlock.Url u) text.append(' ').append(u.label());
                if (block instanceof HandbookBlock.Command c) text.append(' ').append(c.label());
            }
        }
        return plain(text.toString()).toLowerCase(Locale.ROOT);
    }
}
