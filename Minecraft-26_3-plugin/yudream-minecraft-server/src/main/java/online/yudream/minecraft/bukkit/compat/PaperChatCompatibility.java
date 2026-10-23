package online.yudream.minecraft.bukkit.compat;

import java.lang.reflect.Method;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import org.bukkit.entity.Player;import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public final class PaperChatCompatibility {
   private static final String PAPER_ASYNC_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";

   private PaperChatCompatibility() {
   }

   public static boolean register(final YudreamMinecraftPlugin plugin) {
      Class<? extends Event> eventClass;
      try {
         eventClass = Class.forName("io.papermc.paper.event.player.AsyncChatEvent").asSubclass(Event.class);
      } catch (ClassNotFoundException var4) {
         return false;
      } catch (Throwable t) {
         plugin.getLogger().log(Level.FINE, "Could not inspect Paper chat event compatibility", t);
         return false;
      }

      Listener listener = new Listener() {
      };
      EventExecutor executor = new EventExecutor() {
         public void execute(Listener listener, Event event) throws EventException {
            if (plugin.getSettings().isResetOnChat()) {
               final Player player = PaperChatCompatibility.player(event);
               if (player != null) {
                  FoliaSchedulerCompat.runFor(plugin, player, new Runnable() {
                     public void run() {
                        if (player.isOnline()) {
                           plugin.handleActivity(player, "chat");
                        }

                     }
                  });
               }
            }
         }
      };
      plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
      return true;
   }

   private static Player player(Event event) {
      try {
         Method method = event.getClass().getMethod("getPlayer");
         Object player = method.invoke(event);
         return player instanceof Player ? (Player)player : null;
      } catch (Exception var3) {
         return null;
      }
   }
}
