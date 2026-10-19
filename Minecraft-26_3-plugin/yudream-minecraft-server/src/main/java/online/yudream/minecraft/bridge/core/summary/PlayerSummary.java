package online.yudream.minecraft.bridge.core.summary;

import java.util.Collections;
import java.util.List;
import online.yudream.minecraft.bridge.core.json.JsonValue;

public final class PlayerSummary {
   private static final String[] ARRAY_FIELDS = new String[]{"items", "records", "list", "data", "content", "players", "rows"};
   private final int total;
   private final int online;
   private final int afk;

   public PlayerSummary(int total, int online, int afk) {
      this.total = total;
      this.online = online;
      this.afk = afk;
   }

   public int total() {
      return this.total;
   }

   public int online() {
      return this.online;
   }

   public int afk() {
      return this.afk;
   }

   public static PlayerSummary parse(String json) {
      JsonValue root = JsonValue.tryParse(json == null ? "" : json);
      if (root == null) {
         return new PlayerSummary(0, 0, 0);
      } else {
         List<JsonValue> entries = entriesOf(root);
         int online = 0;
         int afk = 0;

         for(JsonValue entry : entries) {
            if (entry.getOr("online", JsonValue.of(false)).asBoolean(false)) {
               ++online;
            }

            if (entry.getOr("afk", JsonValue.of(false)).asBoolean(false)) {
               ++afk;
            }
         }

         long reportedTotal = root.isObject() ? root.getOr("total", root.getOr("totalElements", JsonValue.of((long)entries.size()))).asLong((long)entries.size()) : (long)entries.size();
         return new PlayerSummary((int)Math.max(reportedTotal, 0L), online, afk);
      }
   }

   private static List<JsonValue> entriesOf(JsonValue root) {
      if (root.isArray()) {
         return root.items();
      } else if (!root.isObject()) {
         return Collections.emptyList();
      } else {
         for(String field : ARRAY_FIELDS) {
            JsonValue candidate = root.get(field);
            if (candidate != null && candidate.isArray()) {
               return candidate.items();
            }
         }

         return Collections.emptyList();
      }
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof PlayerSummary)) {
         return false;
      } else {
         PlayerSummary other = (PlayerSummary)o;
         return this.total == other.total && this.online == other.online && this.afk == other.afk;
      }
   }

   public int hashCode() {
      int result = this.total;
      result = 31 * result + this.online;
      result = 31 * result + this.afk;
      return result;
   }

   public String toString() {
      return "PlayerSummary[total=" + this.total + ", online=" + this.online + ", afk=" + this.afk + "]";
   }
}
