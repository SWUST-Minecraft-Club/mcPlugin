package online.yudream.minecraft.bukkit.event;

import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class GameEventListener implements Listener {
   private final YudreamMinecraftPlugin plugin;

   public GameEventListener(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(AsyncPlayerChatEvent event) {
      if (this.plugin.getSettings().getChatBridge().isReportEvents()) {
         final Player player = event.getPlayer();
         final String message = event.getMessage();
         FoliaSchedulerCompat.runFor(this.plugin, player, new Runnable() {
            public void run() {
               if (player.isOnline()) {
                  GameEventListener.this.plugin.handleGameChat(player, message);
               }

            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent event) {
      if (this.plugin.getSettings().getChatBridge().isReportEvents()) {
         String message = event.getDeathMessage();
         if (message != null && !message.isEmpty()) {
            this.plugin.handleGameDeath(event.getEntity(), message);
         }
      }
   }
}
