package online.yudream.minecraft.bukkit.util;

import online.yudream.minecraft.bukkit.report.PlayerEventPayload;
import org.bukkit.entity.Player;

public final class PlayerSnapshots {
   private PlayerSnapshots() {
   }

   public static PlayerEventPayload from(Player player) {
      return from(player, System.currentTimeMillis());
   }

   public static PlayerEventPayload from(Player player, long eventAt) {
      return new PlayerEventPayload(player.getUniqueId().toString(), player.getName(), eventAt);
   }
}
