package com.example.mainmenu.sidecard;

import com.example.mainmenu.api.MenuSidecard;
import com.example.mainmenu.api.MenuSidecardRegistry;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public final class SidecardRegistry implements MenuSidecardRegistry, Listener {
    private record Entry(Plugin owner, MenuSidecard card) { }
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void register(final Plugin owner, final MenuSidecard card) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(card, "card");
        if (!owner.isEnabled()) throw new IllegalArgumentException("Sidecard owner is disabled");
        if (card.id().startsWith("yaml:")) throw new IllegalArgumentException("yaml: is reserved for sidecards.yml");
        if (entries.putIfAbsent(card.id(), new Entry(owner, card)) != null) {
            throw new IllegalArgumentException("Duplicate sidecard id: " + card.id());
        }
        // A disable may race a registration from another region.
        if (!owner.isEnabled()) entries.computeIfPresent(card.id(), (id, entry) -> entry.owner() == owner ? null : entry);
    }

    @Override public boolean unregister(final String id) { return entries.remove(id) != null; }
    @Override public void unregisterAll(final Plugin owner) { entries.entrySet().removeIf(e -> e.getValue().owner() == owner); }
    @Override public Collection<MenuSidecard> cards() {
        return entries.values().stream().filter(e -> e.owner().isEnabled()).map(Entry::card)
                .sorted(Comparator.comparingInt(MenuSidecard::order).thenComparing(MenuSidecard::id)).toList();
    }
    public void clear() { entries.clear(); }
    @EventHandler public void disabled(final PluginDisableEvent event) { unregisterAll(event.getPlugin()); }
}
