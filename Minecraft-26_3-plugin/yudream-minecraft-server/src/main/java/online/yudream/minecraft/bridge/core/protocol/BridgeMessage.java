package online.yudream.minecraft.bridge.core.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import online.yudream.minecraft.bridge.core.json.JsonValue;
import online.yudream.minecraft.bridge.core.model.PlayerIdentity;

public interface BridgeMessage {
   String TYPE_HELLO = "hello";
   String TYPE_HELLO_ACK = "hello_ack";
   String TYPE_PROBE = "probe";
   String TYPE_EVENT = "event";
   String TYPE_GROUP_MSG = "group_msg";
   String KIND_JOIN = "join";
   String KIND_QUIT = "quit";
   String KIND_ACTIVITY = "activity";
   String KIND_CHAT = "chat";
   String KIND_DEATH = "death";
   String KIND_ADVANCEMENT = "advancement";
   String SOURCE_CHAT = "chat";
   String SOURCE_MOVE = "move";
   String SOURCE_INTERACT = "interact";
   String SOURCE_COMMAND = "command";
   String SOURCE_SERVER_SWITCH = "server-switch";

   String type();

   JsonValue toJson();

   public static final class Hello implements BridgeMessage {
      private final String serverName;
      private final String modVersion;
      private final int protocolVersion;
      private final List<PlayerIdentity> players;
      private final long at;

      public Hello(String serverName, String modVersion, int protocolVersion, List<PlayerIdentity> players, long at) {
         this.serverName = serverName;
         this.modVersion = modVersion;
         this.protocolVersion = protocolVersion;
         this.players = players == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(players));
         this.at = at;
      }

      public String serverName() {
         return this.serverName;
      }

      public String modVersion() {
         return this.modVersion;
      }

      public int protocolVersion() {
         return this.protocolVersion;
      }

      public List<PlayerIdentity> players() {
         return this.players;
      }

      public long at() {
         return this.at;
      }

      public String type() {
         return "hello";
      }

      public JsonValue toJson() {
         JsonValue players = JsonValue.array();

         for(PlayerIdentity player : this.players) {
            players.add(JsonValue.object().put("id", player.uuidString()).put("name", player.name()));
         }

         return JsonValue.object().put("t", "hello").put("protocol", (long)this.protocolVersion).put("mod", this.modVersion).put("server", this.serverName).put("at", this.at).put("players", players);
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof Hello)) {
            return false;
         } else {
            Hello other = (Hello)o;
            boolean var10000;
            if (this.protocolVersion == other.protocolVersion && this.at == other.at) {
               label43: {
                  if (this.serverName == null) {
                     if (other.serverName != null) {
                        break label43;
                     }
                  } else if (!this.serverName.equals(other.serverName)) {
                     break label43;
                  }

                  if (this.modVersion == null) {
                     if (other.modVersion != null) {
                        break label43;
                     }
                  } else if (!this.modVersion.equals(other.modVersion)) {
                     break label43;
                  }

                  if (this.players.equals(other.players)) {
                     var10000 = true;
                     return var10000;
                  }
               }
            }

            var10000 = false;
            return var10000;
         }
      }

      public int hashCode() {
         int result = this.serverName == null ? 0 : this.serverName.hashCode();
         result = 31 * result + (this.modVersion == null ? 0 : this.modVersion.hashCode());
         result = 31 * result + this.protocolVersion;
         result = 31 * result + this.players.hashCode();
         result = 31 * result + (int)(this.at ^ this.at >>> 32);
         return result;
      }

      public String toString() {
         return "Hello[serverName=" + this.serverName + ", modVersion=" + this.modVersion + ", protocolVersion=" + this.protocolVersion + ", players=" + this.players + ", at=" + this.at + "]";
      }
   }

   public static final class Event implements BridgeMessage {
      private final String serverName;
      private final String kind;
      private final String source;
      private final PlayerIdentity player;
      private final long at;
      private final String content;

      public Event(String serverName, String kind, String source, PlayerIdentity player, long at, String content) {
         this.serverName = serverName;
         this.kind = kind;
         this.source = source;
         this.player = player;
         this.at = at;
         this.content = content;
      }

      public Event(String serverName, String kind, String source, PlayerIdentity player, long at) {
         this(serverName, kind, source, player, at, (String)null);
      }

      public String serverName() {
         return this.serverName;
      }

      public String kind() {
         return this.kind;
      }

      public String source() {
         return this.source;
      }

      public PlayerIdentity player() {
         return this.player;
      }

      public long at() {
         return this.at;
      }

      public String content() {
         return this.content;
      }

      public String type() {
         return "event";
      }

      public JsonValue toJson() {
         JsonValue body = JsonValue.object().put("t", "event").put("server", this.serverName).put("kind", this.kind).put("at", this.at);
         if (this.source != null) {
            body.put("source", this.source);
         }

         if (this.content != null && !this.content.isEmpty()) {
            body.put("content", this.content);
         }

         if (this.player != null) {
            body.put("player", JsonValue.object().put("id", this.player.uuidString()).put("name", this.player.name()));
         }

         return body;
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof Event)) {
            return false;
         } else {
            boolean var10000;
            label73: {
               Event other = (Event)o;
               if (this.at == other.at) {
                  label67: {
                     if (this.serverName == null) {
                        if (other.serverName != null) {
                           break label67;
                        }
                     } else if (!this.serverName.equals(other.serverName)) {
                        break label67;
                     }

                     if (this.kind == null) {
                        if (other.kind != null) {
                           break label67;
                        }
                     } else if (!this.kind.equals(other.kind)) {
                        break label67;
                     }

                     if (this.source == null) {
                        if (other.source != null) {
                           break label67;
                        }
                     } else if (!this.source.equals(other.source)) {
                        break label67;
                     }

                     if (this.player == null) {
                        if (other.player != null) {
                           break label67;
                        }
                     } else if (!this.player.equals(other.player)) {
                        break label67;
                     }

                     if (this.content == null) {
                        if (other.content == null) {
                           break label73;
                        }
                     } else if (this.content.equals(other.content)) {
                        break label73;
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
         int result = this.serverName == null ? 0 : this.serverName.hashCode();
         result = 31 * result + (this.kind == null ? 0 : this.kind.hashCode());
         result = 31 * result + (this.source == null ? 0 : this.source.hashCode());
         result = 31 * result + (this.player == null ? 0 : this.player.hashCode());
         result = 31 * result + (int)(this.at ^ this.at >>> 32);
         result = 31 * result + (this.content == null ? 0 : this.content.hashCode());
         return result;
      }

      public String toString() {
         return "Event[serverName=" + this.serverName + ", kind=" + this.kind + ", source=" + this.source + ", player=" + this.player + ", at=" + this.at + ", content=" + this.content + "]";
      }
   }

   public static final class GroupMessage implements BridgeMessage {
      private final String sender;
      private final String content;
      private final long at;

      public GroupMessage(String sender, String content, long at) {
         this.sender = sender;
         this.content = content;
         this.at = at;
      }

      public String sender() {
         return this.sender;
      }

      public String content() {
         return this.content;
      }

      public long at() {
         return this.at;
      }

      public String type() {
         return "group_msg";
      }

      public JsonValue toJson() {
         return JsonValue.object().put("t", "group_msg").put("sender", this.sender).put("content", this.content).put("at", this.at);
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof GroupMessage)) {
            return false;
         } else {
            boolean var10000;
            label49: {
               GroupMessage other = (GroupMessage)o;
               if (this.at == other.at) {
                  label43: {
                     if (this.sender == null) {
                        if (other.sender != null) {
                           break label43;
                        }
                     } else if (!this.sender.equals(other.sender)) {
                        break label43;
                     }

                     if (this.content == null) {
                        if (other.content == null) {
                           break label49;
                        }
                     } else if (this.content.equals(other.content)) {
                        break label49;
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
         int result = this.sender == null ? 0 : this.sender.hashCode();
         result = 31 * result + (this.content == null ? 0 : this.content.hashCode());
         result = 31 * result + (int)(this.at ^ this.at >>> 32);
         return result;
      }

      public String toString() {
         return "GroupMessage[sender=" + this.sender + ", content=" + this.content + ", at=" + this.at + "]";
      }
   }

   public static final class Probe implements BridgeMessage {
      private final String proxyVersion;
      private final int protocolVersion;

      public Probe(String proxyVersion, int protocolVersion) {
         this.proxyVersion = proxyVersion;
         this.protocolVersion = protocolVersion;
      }

      public String proxyVersion() {
         return this.proxyVersion;
      }

      public int protocolVersion() {
         return this.protocolVersion;
      }

      public String type() {
         return "probe";
      }

      public JsonValue toJson() {
         return JsonValue.object().put("t", "probe").put("protocol", (long)this.protocolVersion).put("proxy", this.proxyVersion);
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof Probe)) {
            return false;
         } else {
            boolean var10000;
            label36: {
               Probe other = (Probe)o;
               if (this.protocolVersion == other.protocolVersion) {
                  if (this.proxyVersion == null) {
                     if (other.proxyVersion == null) {
                        break label36;
                     }
                  } else if (this.proxyVersion.equals(other.proxyVersion)) {
                     break label36;
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
         int result = this.proxyVersion == null ? 0 : this.proxyVersion.hashCode();
         result = 31 * result + this.protocolVersion;
         return result;
      }

      public String toString() {
         return "Probe[proxyVersion=" + this.proxyVersion + ", protocolVersion=" + this.protocolVersion + "]";
      }
   }

   public static final class HelloAck implements BridgeMessage {
      private final boolean accepting;
      private final String targetServer;
      private final String proxyVersion;
      private final int protocolVersion;

      public HelloAck(boolean accepting, String targetServer, String proxyVersion, int protocolVersion) {
         this.accepting = accepting;
         this.targetServer = targetServer;
         this.proxyVersion = proxyVersion;
         this.protocolVersion = protocolVersion;
      }

      public boolean accepting() {
         return this.accepting;
      }

      public String targetServer() {
         return this.targetServer;
      }

      public String proxyVersion() {
         return this.proxyVersion;
      }

      public int protocolVersion() {
         return this.protocolVersion;
      }

      public String type() {
         return "hello_ack";
      }

      public JsonValue toJson() {
         JsonValue body = JsonValue.object().put("t", "hello_ack").put("protocol", (long)this.protocolVersion).put("proxy", this.proxyVersion).put("accepting", this.accepting);
         if (this.targetServer != null && !this.targetServer.isEmpty()) {
            body.put("target", this.targetServer);
         }

         return body;
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof HelloAck)) {
            return false;
         } else {
            boolean var10000;
            label53: {
               HelloAck other = (HelloAck)o;
               if (this.accepting == other.accepting && this.protocolVersion == other.protocolVersion) {
                  label47: {
                     if (this.targetServer == null) {
                        if (other.targetServer != null) {
                           break label47;
                        }
                     } else if (!this.targetServer.equals(other.targetServer)) {
                        break label47;
                     }

                     if (this.proxyVersion == null) {
                        if (other.proxyVersion == null) {
                           break label53;
                        }
                     } else if (this.proxyVersion.equals(other.proxyVersion)) {
                        break label53;
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
         int result = this.accepting ? 1 : 0;
         result = 31 * result + (this.targetServer == null ? 0 : this.targetServer.hashCode());
         result = 31 * result + (this.proxyVersion == null ? 0 : this.proxyVersion.hashCode());
         result = 31 * result + this.protocolVersion;
         return result;
      }

      public String toString() {
         return "HelloAck[accepting=" + this.accepting + ", targetServer=" + this.targetServer + ", proxyVersion=" + this.proxyVersion + ", protocolVersion=" + this.protocolVersion + "]";
      }
   }
}
