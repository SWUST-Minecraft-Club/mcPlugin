package online.yudream.minecraft.bridge.core.model;

public final class PlayerEventPayload {
   private final String playerId;
   private final String playerName;
   private final long eventAt;
   private final String serverName;
   private final String content;

   public PlayerEventPayload(String playerId, String playerName, long eventAt, String serverName, String content) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.eventAt = eventAt;
      this.serverName = serverName;
      this.content = content;
   }

   public PlayerEventPayload(String playerId, String playerName, long eventAt) {
      this(playerId, playerName, eventAt, (String)null, (String)null);
   }

   public PlayerEventPayload(String playerId, String playerName, long eventAt, String serverName) {
      this(playerId, playerName, eventAt, serverName, (String)null);
   }

   public String playerId() {
      return this.playerId;
   }

   public String playerName() {
      return this.playerName;
   }

   public long eventAt() {
      return this.eventAt;
   }

   public String serverName() {
      return this.serverName;
   }

   public String content() {
      return this.content;
   }

   public static PlayerEventPayload of(PlayerIdentity identity, long eventAt, String serverName) {
      return new PlayerEventPayload(identity.uuidString(), identity.name(), eventAt, serverName, (String)null);
   }

   public static PlayerEventPayload of(PlayerIdentity identity, long eventAt, String serverName, String content) {
      return new PlayerEventPayload(identity.uuidString(), identity.name(), eventAt, serverName, content);
   }

   public PlayerEventPayload withServerName(String serverName) {
      return new PlayerEventPayload(this.playerId, this.playerName, this.eventAt, serverName, this.content);
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof PlayerEventPayload)) {
         return false;
      } else {
         boolean var10000;
         label65: {
            PlayerEventPayload other = (PlayerEventPayload)o;
            if (this.eventAt == other.eventAt) {
               label59: {
                  if (this.playerId == null) {
                     if (other.playerId != null) {
                        break label59;
                     }
                  } else if (!this.playerId.equals(other.playerId)) {
                     break label59;
                  }

                  if (this.playerName == null) {
                     if (other.playerName != null) {
                        break label59;
                     }
                  } else if (!this.playerName.equals(other.playerName)) {
                     break label59;
                  }

                  if (this.serverName == null) {
                     if (other.serverName != null) {
                        break label59;
                     }
                  } else if (!this.serverName.equals(other.serverName)) {
                     break label59;
                  }

                  if (this.content == null) {
                     if (other.content == null) {
                        break label65;
                     }
                  } else if (this.content.equals(other.content)) {
                     break label65;
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
      int result = this.playerId == null ? 0 : this.playerId.hashCode();
      result = 31 * result + (this.playerName == null ? 0 : this.playerName.hashCode());
      result = 31 * result + (int)(this.eventAt ^ this.eventAt >>> 32);
      result = 31 * result + (this.serverName == null ? 0 : this.serverName.hashCode());
      result = 31 * result + (this.content == null ? 0 : this.content.hashCode());
      return result;
   }

   public String toString() {
      return "PlayerEventPayload[playerId=" + this.playerId + ", playerName=" + this.playerName + ", eventAt=" + this.eventAt + ", serverName=" + this.serverName + ", content=" + this.content + "]";
   }
}
