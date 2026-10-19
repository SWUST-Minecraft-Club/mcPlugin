package online.yudream.minecraft.bukkit.api;

public final class HttpResult {
   private final int statusCode;
   private final String body;

   public HttpResult(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body == null ? "" : body;
   }

   public int getStatusCode() {
      return this.statusCode;
   }

   public String getBody() {
      return this.body;
   }

   public boolean isSuccess() {
      return this.statusCode >= 200 && this.statusCode < 300;
   }

   public String toString() {
      return "HTTP " + this.statusCode + " " + this.body;
   }
}
