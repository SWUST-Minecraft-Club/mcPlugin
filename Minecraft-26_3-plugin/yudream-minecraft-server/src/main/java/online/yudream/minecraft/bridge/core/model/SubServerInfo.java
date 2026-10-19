package online.yudream.minecraft.bridge.core.model;

public final class SubServerInfo {
   private final String name;
   private final String address;
   private final int online;
   private final boolean sensor;
   private final boolean defaultServer;

   public SubServerInfo(String name, String address, int online, boolean sensor, boolean defaultServer) {
      this.name = name;
      this.address = address;
      this.online = online;
      this.sensor = sensor;
      this.defaultServer = defaultServer;
   }

   public String name() {
      return this.name;
   }

   public String address() {
      return this.address;
   }

   public int online() {
      return this.online;
   }

   public boolean sensor() {
      return this.sensor;
   }

   public boolean defaultServer() {
      return this.defaultServer;
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof SubServerInfo)) {
         return false;
      } else {
         boolean var10000;
         label57: {
            SubServerInfo other = (SubServerInfo)o;
            if (this.online == other.online && this.sensor == other.sensor && this.defaultServer == other.defaultServer) {
               label51: {
                  if (this.name == null) {
                     if (other.name != null) {
                        break label51;
                     }
                  } else if (!this.name.equals(other.name)) {
                     break label51;
                  }

                  if (this.address == null) {
                     if (other.address == null) {
                        break label57;
                     }
                  } else if (this.address.equals(other.address)) {
                     break label57;
                  }
               }
            }

            var10000 = false;
            return var10000;
         }

         var10000 = true;
         return var10000;
      }
   }

   public int hashCode() {
      int result = this.name == null ? 0 : this.name.hashCode();
      result = 31 * result + (this.address == null ? 0 : this.address.hashCode());
      result = 31 * result + this.online;
      result = 31 * result + (this.sensor ? 1 : 0);
      result = 31 * result + (this.defaultServer ? 1 : 0);
      return result;
   }

   public String toString() {
      return "SubServerInfo[name=" + this.name + ", address=" + this.address + ", online=" + this.online + ", sensor=" + this.sensor + ", defaultServer=" + this.defaultServer + "]";
   }
}
