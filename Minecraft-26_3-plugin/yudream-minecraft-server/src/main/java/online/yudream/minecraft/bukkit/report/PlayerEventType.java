package online.yudream.minecraft.bukkit.report;

public enum PlayerEventType {
   JOIN("players/join"),
   QUIT("players/quit"),
   AFK_START("players/afk/start"),
   AFK_END("players/afk/end"),
   CHAT("events/chat"),
   DEATH("events/death"),
   ADVANCEMENT("events/advancement");

   private final String remotePath;

   private PlayerEventType(String remotePath) {
      this.remotePath = remotePath;
   }

   public String getRemotePath() {
      return this.remotePath;
   }

   // $FF: synthetic method
   private static PlayerEventType[] $values() {
      return new PlayerEventType[]{JOIN, QUIT, AFK_START, AFK_END, CHAT, DEATH, ADVANCEMENT};
   }
}
