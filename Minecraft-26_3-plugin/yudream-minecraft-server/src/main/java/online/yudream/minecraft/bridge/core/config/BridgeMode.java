package online.yudream.minecraft.bridge.core.config;

import java.util.Locale;

public enum BridgeMode {
   DOWNSTREAM("downstream"),
   STANDALONE("standalone");

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

   public boolean isStandalone() {
      return this == STANDALONE;
   }

   public static BridgeMode fromId(String value, BridgeMode fallback) {
      if (value != null) {
         String trimmed = value.trim().toLowerCase(Locale.ROOT);

         for(BridgeMode mode : values()) {
            if (mode.id.equals(trimmed)) {
               return mode;
            }
         }
      }

      return fallback;
   }

   // $FF: synthetic method
   private static BridgeMode[] $values() {
      return new BridgeMode[]{DOWNSTREAM, STANDALONE};
   }
}
