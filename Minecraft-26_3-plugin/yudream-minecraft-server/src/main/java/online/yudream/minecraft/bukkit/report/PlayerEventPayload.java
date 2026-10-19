package online.yudream.minecraft.bukkit.report;

public final class PlayerEventPayload {
   private final String playerId;
   private final String playerName;
   private final long eventAt;
   private final String content;

   public PlayerEventPayload(String playerId, String playerName, long eventAt) {
      this(playerId, playerName, eventAt, (String)null);
   }

   public PlayerEventPayload(String playerId, String playerName, long eventAt, String content) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.eventAt = eventAt;
      this.content = content;
   }

   public String getPlayerId() {
      return this.playerId;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public long getEventAt() {
      return this.eventAt;
   }

   public String getContent() {
      return this.content;
   }
}
