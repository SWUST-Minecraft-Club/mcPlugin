package online.yudream.minecraft.bridge.core.afk;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import online.yudream.minecraft.bridge.core.config.BridgeSettings;
import online.yudream.minecraft.bridge.core.model.PlayerEventPayload;
import online.yudream.minecraft.bridge.core.model.PlayerEventType;
import online.yudream.minecraft.bridge.core.model.PlayerIdentity;

public final class AfkTracker {
   private final Sink sink;
   private final Map<UUID, Entry> entries = new ConcurrentHashMap();

   public AfkTracker(Sink sink) {
      this.sink = sink;
   }

   public void markOnline(PlayerIdentity player, long now) {
      if (player != null) {
         this.entries.put(player.uuid(), new Entry(now, false));
      }
   }

   public void markOffline(UUID uuid) {
      if (uuid != null) {
         this.entries.remove(uuid);
      }

   }

   public void markActive(PlayerIdentity player, String serverName, long now, BridgeSettings settings) {
      if (player != null && settings != null && settings.isAfkEnabled()) {
         Entry previous = (Entry)this.entries.get(player.uuid());
         if (previous == null) {
            this.entries.put(player.uuid(), new Entry(now, false));
         } else {
            if (previous.afk) {
               this.sink.submit(PlayerEventType.AFK_END, PlayerEventPayload.of(player, now, serverName));
            }

            this.entries.put(player.uuid(), new Entry(now, false));
         }
      }
   }

   public boolean isAfk(UUID uuid) {
      Entry entry = (Entry)this.entries.get(uuid);
      return entry != null && entry.afk;
   }

   public int tick(Collection<PlayerIdentity> onlinePlayers, String serverName, long now, BridgeSettings settings) {
      if (settings != null && settings.isAfkEnabled()) {
         int transitions = 0;

         for(PlayerIdentity player : onlinePlayers) {
            Entry entry = (Entry)this.entries.get(player.uuid());
            if (entry == null) {
               this.entries.put(player.uuid(), new Entry(now, false));
            } else if (!entry.afk && now - entry.lastActiveAt >= settings.getAfkTimeoutMs()) {
               this.entries.put(player.uuid(), new Entry(entry.lastActiveAt, true));
               this.sink.submit(PlayerEventType.AFK_START, PlayerEventPayload.of(player, now, serverName));
               ++transitions;
            }
         }

         return transitions;
      } else {
         return 0;
      }
   }

   public void retainOnly(Collection<UUID> onlineUuids) {
      this.entries.keySet().retainAll(onlineUuids);
   }

   public void reset() {
      this.entries.clear();
   }

   public int trackedPlayers() {
      return this.entries.size();
   }

   private static final class Entry {
      private final long lastActiveAt;
      private final boolean afk;

      private Entry(long lastActiveAt, boolean afk) {
         this.lastActiveAt = lastActiveAt;
         this.afk = afk;
      }
   }

   public interface Sink {
      void submit(PlayerEventType var1, PlayerEventPayload var2);
   }
}
