package online.yudream.minecraft.bukkit.event;

import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

public final class PlayerActivityListener implements Listener {
   private final YudreamMinecraftPlugin plugin;

   public PlayerActivityListener(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.handleJoin(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent event) {
      this.plugin.handleQuit(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.plugin.getSettings().isResetOnMove() && movedBlock(event.getFrom(), event.getTo())) {
         this.plugin.handleActivity(event.getPlayer(), "move");
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(final AsyncPlayerChatEvent event) {
      if (this.plugin.getSettings().isResetOnChat()) {
         final Player player = event.getPlayer();
         FoliaSchedulerCompat.runFor(this.plugin, player, new Runnable() {
            public void run() {
               PlayerActivityListener.this.plugin.handleActivity(player, "chat");
            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.plugin.getSettings().isResetOnCommand()) {
         this.plugin.handleActivity(event.getPlayer(), "command");
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      if (this.plugin.getSettings().isResetOnInteract()) {
         this.plugin.handleActivity(event.getPlayer(), "interact");
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSneak(PlayerToggleSneakEvent event) {
      this.plugin.handleActivity(event.getPlayer(), "interact");
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSprint(PlayerToggleSprintEvent event) {
      this.plugin.handleActivity(event.getPlayer(), "interact");
   }

   private static boolean movedBlock(Location from, Location to) {
      return to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ());
   }
}
