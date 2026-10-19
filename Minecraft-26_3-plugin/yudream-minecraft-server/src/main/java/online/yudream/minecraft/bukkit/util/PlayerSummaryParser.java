package online.yudream.minecraft.bukkit.util;

public final class PlayerSummaryParser {
   private PlayerSummaryParser() {
   }

   public static Summary parse(String json) {
      return json != null && !json.isEmpty() ? new Summary(count(json, "\"playerId\""), count(json, "\"online\":true"), count(json, "\"afk\":true")) : new Summary(0, 0, 0);
   }

   private static int count(String text, String needle) {
      int count = 0;

      int index;
      for(index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
         ++count;
      }

      return count;
   }

   public static final class Summary {
      private final int total;
      private final int online;
      private final int afk;

      private Summary(int total, int online, int afk) {
         this.total = total;
         this.online = online;
         this.afk = afk;
      }

      public int getTotal() {
         return this.total;
      }

      public int getOnline() {
         return this.online;
      }

      public int getAfk() {
         return this.afk;
      }
   }
}
