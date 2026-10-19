package online.yudream.minecraft.bridge.core.model;

public enum PlayerEventType {
   JOIN("players/join"),
   QUIT("players/quit"),
   AFK_START("players/afk/start"),
   AFK_END("players/afk/end"),
   CHAT("events/chat"),
   DEATH("events/death"),
   ADVANCEMENT("events/advancement"),
   SERVER_SWITCH("players/server/switch");

   private final String remotePath;

   private PlayerEventType(String remotePath) {
      this.remotePath = remotePath;
   }

   public String getRemotePath() {
      return this.remotePath;
   }

   public static PlayerEventType fromRemotePath(String remotePath) {
      for(PlayerEventType type : values()) {
         if (type.remotePath.equals(remotePath)) {
            return type;
         }
      }

      return null;
   }

   // $FF: synthetic method
   private static PlayerEventType[] $values() {
      return new PlayerEventType[]{JOIN, QUIT, AFK_START, AFK_END, CHAT, DEATH, ADVANCEMENT, SERVER_SWITCH};
   }
}
