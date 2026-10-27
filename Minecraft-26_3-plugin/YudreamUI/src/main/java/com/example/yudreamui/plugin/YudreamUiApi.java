package com.example.yudreamui.plugin;
import com.google.gson.*;
import com.example.yudreamui.protocol.FallbackPlan;
import java.util.Set;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;

/** Provider API, invoked on the player's Folia entity scheduler. Documents contain no executable JavaScript. */
public interface YudreamUiApi {
    record Document(String title,String html,JsonArray assets,JsonObject state,Set<String> actions,FallbackPlan fallback) {
        public Document(String title,String html,JsonArray assets,JsonObject state,Set<String> actions) {
            this(title,html,assets,state,actions,FallbackPlan.fromHtml(html,title));
        }
    }
    record Action(String name,String element,JsonObject form) {}
    void registerFallback(FallbackProvider provider);
    boolean isClientReady(Player player);
    void open(Player player,Document document,BiConsumer<Player,Action> handler);
    void patch(Player player,JsonObject state);
    void close(Player player);
    void openDialogue(Player player,String id);
    void openShop(Player player);
}
