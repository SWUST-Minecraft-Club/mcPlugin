package com.example.handbookcore.internal;

import com.example.handbookcore.api.*;
import com.example.handbookcore.render.HandbookViews;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class DefaultHandbookService implements HandbookService {
    private final HandbookCatalog catalog;
    private final HandbookViews views;
    public DefaultHandbookService(HandbookCatalog catalog, HandbookViews views) { this.catalog = catalog; this.views = views; }
    private static String owner(Plugin plugin) {
        if (!plugin.isEnabled()) throw new IllegalStateException("不能以已停用插件注册内容");
        return plugin.getName();
    }
    public void registerCategory(Plugin owner, HandbookCategory category) { catalog.category(owner(owner), category); }
    public void registerEntry(Plugin owner, HandbookEntry entry) { catalog.entry(owner(owner), entry); }
    public void replace(Plugin owner, HandbookContribution contribution) { catalog.replace(owner(owner), contribution); }
    public void unregister(Plugin owner, NamespacedKey id) { catalog.unregister(owner.getName(), id); }
    public void unregisterAll(Plugin owner) { catalog.removeOwner(owner.getName()); }
    public void openHome(Player player) { views.home(player, 0); }
    public boolean openCategory(Player player, NamespacedKey id) {
        if (!catalog.snapshot().categories().containsKey(id)) return false;
        views.category(player, id, 0); return true;
    }
    public boolean openEntry(Player player, NamespacedKey id) {
        if (!catalog.snapshot().entries().containsKey(id)) return false;
        views.entry(player, id, 0); return true;
    }
    public void search(Player player, String query) { views.results(player, query, 0); }
}
