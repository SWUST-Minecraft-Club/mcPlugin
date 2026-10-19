package online.yudream.minecraft.bridge.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SubServerRoster {
   private final String name;
   private final List<PlayerEventPayload> players;

   public SubServerRoster(String name, List<PlayerEventPayload> players) {
      this.name = name;
      this.players = players == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(players));
   }

   public String name() {
      return this.name;
   }

   public List<PlayerEventPayload> players() {
      return this.players;
   }

   public static SubServerRoster empty(String name) {
      return new SubServerRoster(name, Collections.emptyList());
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof SubServerRoster)) {
         return false;
      } else {
         boolean var10000;
         label23: {
            SubServerRoster other = (SubServerRoster)o;
            if (this.name == null) {
               if (other.name != null) {
                  break label23;
               }
            } else if (!this.name.equals(other.name)) {
               break label23;
            }

            if (this.players.equals(other.players)) {
               var10000 = true;
               return var10000;
            }
         }

         var10000 = false;
         return var10000;
      }
   }

   public int hashCode() {
      int result = this.name == null ? 0 : this.name.hashCode();
      result = 31 * result + this.players.hashCode();
      return result;
   }

   public String toString() {
      return "SubServerRoster[name=" + this.name + ", players=" + this.players + "]";
   }
}
