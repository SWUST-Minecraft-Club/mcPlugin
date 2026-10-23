package online.yudream.minecraft.bukkit.compat;

import java.lang.reflect.Method;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public final class AdvancementCompatibility {
   private static final String ADVANCEMENT_DONE_EVENT = "org.bukkit.event.player.PlayerAdvancementDoneEvent";

   private AdvancementCompatibility() {
   }

   public static boolean register(final YudreamMinecraftPlugin plugin) {
      Class<? extends Event> eventClass;
      try {
         eventClass = Class.forName("org.bukkit.event.player.PlayerAdvancementDoneEvent").asSubclass(Event.class);
      } catch (ClassNotFoundException var6) {
         return false;
      } catch (Throwable t) {
         plugin.getLogger().log(Level.FINE, "Could not inspect advancement event compatibility", t);
         return false;
      }

      Listener listener = new Listener() {
      };
      EventExecutor executor = new EventExecutor() {
         public void execute(Listener listener, Event event) throws EventException {
            if (plugin.getSettings().getChatBridge().isReportEvents()) {
               final Player player = AdvancementCompatibility.player(event);
               final String title = AdvancementCompatibility.displayTitle(event);
               if (player != null && title != null && !title.isEmpty()) {
                  FoliaSchedulerCompat.runFor(plugin, player, new Runnable() {
                     public void run() {
                        if (player.isOnline()) {
                           plugin.handleGameAdvancement(player, title);
                        }

                     }
                  });
               }
            }
         }
      };

      try {
         plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
         return true;
      } catch (Throwable t) {
         plugin.getLogger().log(Level.FINE, "Could not register advancement event compatibility", t);
         return false;
      }
   }

   private static Player player(Event event) {
      try {
         Object player = event.getClass().getMethod("getPlayer").invoke(event);
         return player instanceof Player ? (Player)player : null;
      } catch (Exception var2) {
         return null;
      }
   }

   private static String displayTitle(Event event) {
      try {
         Object advancement = event.getClass().getMethod("getAdvancement").invoke(event);
         if (advancement == null) {
            return null;
         } else {
            Method getDisplay = advancement.getClass().getMethod("getDisplay");
            Object display = getDisplay.invoke(advancement);
            if (display == null) {
               return null;
            } else {
               Object announce = display.getClass().getMethod("doesAnnounceToChat").invoke(display);
               if (announce instanceof Boolean && (Boolean)announce) {
                  Object title = display.getClass().getMethod("getTitle").invoke(display);
                  return title instanceof String ? (String)title : null;
               } else {
                  return null;
               }
            }
         }
      } catch (Exception var6) {
         return null;
      }
   }
}
