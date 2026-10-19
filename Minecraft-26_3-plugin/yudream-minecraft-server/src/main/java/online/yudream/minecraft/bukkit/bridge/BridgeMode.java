package online.yudream.minecraft.bukkit.bridge;

import java.util.Locale;

public enum BridgeMode {
   STANDALONE("standalone"),
   DOWNSTREAM("downstream");

   private final String id;

   private BridgeMode(String id) {
      this.id = id;
   }

   public String getId() {
      return this.id;
   }

   public boolean isDownstream() {
      return this == DOWNSTREAM;
   }

   public static BridgeMode fromId(String value) {
      if (value != null) {
         String trimmed = value.trim().toLowerCase(Locale.ROOT);

         for(BridgeMode mode : values()) {
            if (mode.id.equals(trimmed)) {
               return mode;
            }
         }
      }

      return STANDALONE;
   }

   // $FF: synthetic method
   private static BridgeMode[] $values() {
      return new BridgeMode[]{STANDALONE, DOWNSTREAM};
   }
}
