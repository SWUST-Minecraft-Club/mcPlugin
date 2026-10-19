package online.yudream.minecraft.bridge.core.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import online.yudream.minecraft.bridge.core.json.JsonValue;
import online.yudream.minecraft.bridge.core.model.PlayerEventPayload;
import online.yudream.minecraft.bridge.core.model.PlayerEventType;
import online.yudream.minecraft.bridge.core.model.SubServerRoster;

public final class ReportTask {
   private final String id;
   private final boolean snapshot;
   private final PlayerEventType type;
   private final PlayerEventPayload payload;
   private final List<PlayerEventPayload> players;
   private final long observedAt;
   private final String serverName;
   private final List<SubServerRoster> servers;

   public ReportTask(String id, boolean snapshot, PlayerEventType type, PlayerEventPayload payload, List<PlayerEventPayload> players, long observedAt, String serverName, List<SubServerRoster> servers) {
      this.id = id;
      this.snapshot = snapshot;
      this.type = type;
      this.payload = payload;
      this.players = players == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(players));
      this.observedAt = observedAt;
      this.serverName = serverName;
      this.servers = servers == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(servers));
   }

   public String id() {
      return this.id;
   }

   public boolean snapshot() {
      return this.snapshot;
   }

   public PlayerEventType type() {
      return this.type;
   }

   public PlayerEventPayload payload() {
      return this.payload;
   }

   public List<PlayerEventPayload> players() {
      return this.players;
   }

   public long observedAt() {
      return this.observedAt;
   }

   public String serverName() {
      return this.serverName;
   }

   public List<SubServerRoster> servers() {
      return this.servers;
   }

   public static ReportTask event(PlayerEventType type, PlayerEventPayload payload) {
      return new ReportTask(UUID.randomUUID().toString(), false, type, payload, (List)null, payload.eventAt(), payload.serverName(), (List)null);
   }

   public static ReportTask snapshot(Collection<PlayerEventPayload> players, long observedAt, String serverName) {
      return new ReportTask(UUID.randomUUID().toString(), true, (PlayerEventType)null, (PlayerEventPayload)null, new ArrayList(players), observedAt, serverName, (List)null);
   }

   public static ReportTask groupedSnapshot(Collection<SubServerRoster> servers, long observedAt) {
      return new ReportTask(UUID.randomUUID().toString(), true, (PlayerEventType)null, (PlayerEventPayload)null, (List)null, observedAt, (String)null, new ArrayList(servers));
   }

   public boolean grouped() {
      return this.snapshot && !this.servers.isEmpty();
   }

   public JsonValue toJson() {
      JsonValue node = JsonValue.object().put("id", this.id).put("kind", this.snapshot ? "snapshot" : "event");
      if (this.snapshot) {
         node.put("observedAt", this.observedAt);
         if (this.grouped()) {
            JsonValue list = JsonValue.array();

            for(SubServerRoster roster : this.servers) {
               list.add(JsonValue.object().put("name", roster.name() == null ? "" : roster.name()).put("players", playerArray(roster.players())));
            }

            node.put("servers", list);
         } else {
            node.put("server", this.serverName == null ? "" : this.serverName);
            node.put("players", playerArray(this.players));
         }
      } else {
         node.put("type", this.type.getRemotePath());
         node.put("server", this.serverName == null ? "" : this.serverName);
         JsonValue player = JsonValue.object().put("playerId", this.payload.playerId()).put("playerName", this.payload.playerName()).put("eventAt", this.payload.eventAt());
         if (this.payload.content() != null && !this.payload.content().isEmpty()) {
            player.put("content", this.payload.content());
         }

         node.put("player", player);
      }

      return node;
   }

   public static ReportTask fromJson(JsonValue node) {
      if (node != null && node.isObject()) {
         String id = node.getOr("id", JsonValue.of("")).asString("");
         if (id.isEmpty()) {
            return null;
         } else {
            String serverName = node.getOr("server", JsonValue.of("")).asString("");
            if (serverName.isEmpty()) {
               serverName = null;
            }

            String kind = node.getOr("kind", JsonValue.of("event")).asString("event");
            if ("snapshot".equals(kind)) {
               long observedAt = node.getOr("observedAt", JsonValue.of(0L)).asLong(0L);
               JsonValue grouped = node.get("servers");
               if (grouped != null && grouped.isArray() && !grouped.items().isEmpty()) {
                  List<SubServerRoster> servers = new ArrayList();

                  for(JsonValue entry : grouped.items()) {
                     if (entry.isObject()) {
                        String name = entry.getOr("name", JsonValue.of("")).asString("");
                        servers.add(new SubServerRoster(name, playersOf(entry, (String)null)));
                     }
                  }

                  return new ReportTask(id, true, (PlayerEventType)null, (PlayerEventPayload)null, (List)null, observedAt, (String)null, servers);
               } else {
                  return new ReportTask(id, true, (PlayerEventType)null, (PlayerEventPayload)null, playersOf(node, serverName), observedAt, serverName, (List)null);
               }
            } else {
               PlayerEventType type = PlayerEventType.fromRemotePath(node.getOr("type", JsonValue.of("")).asString(""));
               if (type == null) {
                  return null;
               } else {
                  JsonValue player = node.get("player");
                  if (player != null && player.isObject()) {
                     String playerId = player.getOr("playerId", JsonValue.of("")).asString("");
                     if (playerId.isEmpty()) {
                        return null;
                     } else {
                        PlayerEventPayload payload = new PlayerEventPayload(playerId, player.getOr("playerName", JsonValue.of("")).asString(""), player.getOr("eventAt", JsonValue.of(0L)).asLong(0L), serverName, player.has("content") ? player.get("content").asString((String)null) : null);
                        return new ReportTask(id, false, type, payload, (List)null, payload.eventAt(), serverName, (List)null);
                     }
                  } else {
                     return null;
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   private static List<PlayerEventPayload> playersOf(JsonValue node, String serverName) {
      List<PlayerEventPayload> players = new ArrayList();

      for(JsonValue entry : node.getOr("players", JsonValue.array()).items()) {
         String playerId = entry.getOr("playerId", JsonValue.of("")).asString("");
         if (!playerId.isEmpty()) {
            players.add(new PlayerEventPayload(playerId, entry.getOr("playerName", JsonValue.of("")).asString(""), 0L, serverName));
         }
      }

      return players;
   }

   private static JsonValue playerArray(List<PlayerEventPayload> players) {
      JsonValue list = JsonValue.array();

      for(PlayerEventPayload player : players) {
         list.add(JsonValue.object().put("playerId", player.playerId()).put("playerName", player.playerName()));
      }

      return list;
   }

   public String describe(boolean includePayload) {
      if (!this.snapshot) {
         StringBuilder message = new StringBuilder();
         message.append("type=").append(this.type);
         message.append(", endpoint=/").append(this.type.getRemotePath());
         message.append(", player=").append(this.payload.playerName());
         if (this.serverName != null) {
            message.append(", server=").append(this.serverName);
         }

         if (includePayload) {
            message.append(", playerId=").append(this.payload.playerId());
            message.append(", eventAt=").append(this.payload.eventAt());
            if (this.payload.content() != null) {
               message.append(", content=").append(this.payload.content());
            }
         }

         return message.toString();
      } else {
         StringBuilder message = new StringBuilder();
         message.append("type=SNAPSHOT, endpoint=/players/snapshot");
         if (this.grouped()) {
            message.append(", subServers=").append(this.servers.size());
            int total = 0;

            for(SubServerRoster roster : this.servers) {
               total += roster.players().size();
            }

            message.append(", players=").append(total);
         } else {
            message.append(", players=").append(this.players.size());
            if (this.serverName != null) {
               message.append(", server=").append(this.serverName);
            }
         }

         if (includePayload) {
            message.append(", observedAt=").append(this.observedAt);
         }

         return message.toString();
      }
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof ReportTask)) {
         return false;
      } else {
         boolean var10000;
         label67: {
            ReportTask other = (ReportTask)o;
            if (this.snapshot == other.snapshot && this.observedAt == other.observedAt) {
               label60: {
                  if (this.id == null) {
                     if (other.id != null) {
                        break label60;
                     }
                  } else if (!this.id.equals(other.id)) {
                     break label60;
                  }

                  if (this.type == other.type) {
                     label61: {
                        if (this.payload == null) {
                           if (other.payload != null) {
                              break label61;
                           }
                        } else if (!this.payload.equals(other.payload)) {
                           break label61;
                        }

                        if (this.players.equals(other.players)) {
                           if (this.serverName == null) {
                              if (other.serverName == null) {
                                 break label67;
                              }
                           } else if (this.serverName.equals(other.serverName)) {
                              break label67;
                           }
                        }
                     }
                  }
               }
            }

            var10000 = false;
            return var10000;
         }

         var10000 = true;
         return var10000;
      }
   }

   public int hashCode() {
      int result = this.id == null ? 0 : this.id.hashCode();
      result = 31 * result + (this.snapshot ? 1 : 0);
      result = 31 * result + (this.type == null ? 0 : this.type.hashCode());
      result = 31 * result + (this.payload == null ? 0 : this.payload.hashCode());
      result = 31 * result + this.players.hashCode();
      result = 31 * result + (int)(this.observedAt ^ this.observedAt >>> 32);
      result = 31 * result + (this.serverName == null ? 0 : this.serverName.hashCode());
      return result;
   }

   public String toString() {
      return "ReportTask[id=" + this.id + ", snapshot=" + this.snapshot + ", type=" + this.type + ", payload=" + this.payload + ", players=" + this.players + ", observedAt=" + this.observedAt + ", serverName=" + this.serverName + "]";
   }
}
