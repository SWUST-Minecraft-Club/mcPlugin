package com.example.yudreamui.plugin;

import com.example.yudreamui.protocol.FallbackPlan;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

/** Optional menu-plugin adapter. All calls and callbacks run on the viewer's Folia entity scheduler. */
public interface FallbackProvider {
    String id();
    Handle open(Player player, Context context);
    interface Handle {
        void update();
        default void suspend() {}
        void close();
    }
    interface Context {
        FallbackPlan plan();
        JsonObject state();
        String ticket();
        boolean active();
        void invoke(String controlId, JsonObject form);
        void edit(String controlId);
        void close();
    }
}
