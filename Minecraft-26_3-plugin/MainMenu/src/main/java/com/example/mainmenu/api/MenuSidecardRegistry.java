package com.example.mainmenu.api;

import java.util.Collection;
import org.bukkit.plugin.Plugin;

/** Registration is independent of application icons and never enables sidecards globally. */
public interface MenuSidecardRegistry {
    void register(Plugin owner, MenuSidecard card);
    boolean unregister(String id);
    void unregisterAll(Plugin owner);
    Collection<MenuSidecard> cards();
}
