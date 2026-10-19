package online.yudream.minecraft.bridge.core.model;

import java.util.Objects;
import java.util.UUID;

public final class PlayerIdentity {
   private final UUID uuid;
   private final String name;

   public PlayerIdentity(UUID uuid, String name) {
      Objects.requireNonNull(uuid, "uuid");
      this.uuid = uuid;
      this.name = name == null ? "" : name;
   }

   public PlayerEventPayload payload(long eventAt) {
      return PlayerEventPayload.of(this, eventAt, (String)null);
   }

   public UUID uuid() {
      return this.uuid;
   }

   public String name() {
      return this.name;
   }

   public String uuidString() {
      return this.uuid.toString();
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof PlayerIdentity)) {
         return false;
      } else {
         PlayerIdentity other = (PlayerIdentity)o;
         return this.uuid.equals(other.uuid) && this.name.equals(other.name);
      }
   }

   public int hashCode() {
      return 31 * this.uuid.hashCode() + this.name.hashCode();
   }

   public String toString() {
      return "PlayerIdentity[uuid=" + this.uuid + ", name=" + this.name + "]";
   }
}
