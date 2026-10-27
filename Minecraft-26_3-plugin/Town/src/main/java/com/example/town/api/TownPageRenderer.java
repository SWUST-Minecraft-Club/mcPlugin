package com.example.town.api;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

/** Replaceable Town presentation; Town retains commands, permissions, drafts and transactions. */
public interface TownPageRenderer {
    boolean open(Player player, String page, String source, UnaryOperator<String> resolve, Consumer<String> action);
    boolean active(Player player, String page);
    void close(Player player);
    default boolean suppliesClaimGrid(Player player) { return false; }
    default boolean input(Player player, String title, String initial, int maximum,
                          Consumer<String> submitted, Runnable cancelled) { return false; }
}
