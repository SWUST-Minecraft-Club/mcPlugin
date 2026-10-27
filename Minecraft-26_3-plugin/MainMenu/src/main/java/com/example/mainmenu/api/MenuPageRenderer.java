package com.example.mainmenu.api;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

/** Optional presentation service. All methods run on the viewer's Folia entity thread.
 * Returning false delegates to the existing renderer. Source and callbacks stay on the server. */
public interface MenuPageRenderer {
    boolean open(Player player, String page, String source, UnaryOperator<String> resolve, Consumer<String> action);
    boolean active(Player player, String page);
    void close(Player player);
    default boolean openHome(Player player) { return false; }
    default boolean input(Player player, String title, String initial, int maximum,
                          Consumer<String> submitted, Runnable cancelled) { return false; }
}
