package online.yudream.minecraft.bukkit.util;

import java.util.Collection;
import online.yudream.minecraft.bukkit.report.PlayerEventPayload;

public final class JsonObjects {
   private JsonObjects() {
   }

   public static String playerEvent(PlayerEventPayload payload) {
      StringBuilder json = (new StringBuilder("{")).append("\"playerId\":\"").append(escape(payload.getPlayerId())).append("\",").append("\"playerName\":\"").append(escape(payload.getPlayerName())).append("\",").append("\"eventAt\":").append(payload.getEventAt());
      if (payload.getContent() != null) {
         json.append(",\"content\":\"").append(escape(payload.getContent())).append("\"");
      }

      return json.append("}").toString();
   }

   public static String playerSnapshot(Collection<PlayerEventPayload> players, long observedAt) {
      StringBuilder json = (new StringBuilder("{\"observedAt\":")).append(observedAt).append(",\"players\":[");
      boolean first = true;

      for(PlayerEventPayload player : players) {
         if (!first) {
            json.append(',');
         }

         first = false;
         json.append("{\"playerId\":\"").append(escape(player.getPlayerId())).append("\",\"playerName\":\"").append(escape(player.getPlayerName())).append("\"}");
      }

      return json.append("]}").toString();
   }

   public static String escape(String value) {
      if (value == null) {
         return "";
      } else {
         StringBuilder result = new StringBuilder(value.length() + 16);

         for(int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            String hex;
            int pad;
            switch (c) {
               case '\b':
                  result.append("\\b");
                  continue;
               case '\t':
                  result.append("\\t");
                  continue;
               case '\n':
                  result.append("\\n");
                  continue;
               case '\f':
                  result.append("\\f");
                  continue;
               case '\r':
                  result.append("\\r");
                  continue;
               case '"':
                  result.append("\\\"");
                  continue;
               case '\\':
                  result.append("\\\\");
                  continue;
               default:
                  if (c >= ' ') {
                     result.append(c);
                     continue;
                  }

                  hex = Integer.toHexString(c);
                  result.append("\\u");
                  pad = hex.length();
            }

            while(pad < 4) {
               result.append('0');
               ++pad;
            }

            result.append(hex);
         }

         return result.toString();
      }
   }
}
