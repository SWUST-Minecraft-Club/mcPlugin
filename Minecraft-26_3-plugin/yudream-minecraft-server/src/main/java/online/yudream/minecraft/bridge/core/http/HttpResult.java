package online.yudream.minecraft.bridge.core.http;

public final class HttpResult {
   private final int statusCode;
   private final String body;

   public HttpResult(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
   }

   public int statusCode() {
      return this.statusCode;
   }

   public String body() {
      return this.body;
   }

   public boolean isSuccess() {
      return this.statusCode >= 200 && this.statusCode < 300;
   }

   public String trimmedBody() {
      if (this.body == null) {
         return "";
      } else {
         return this.body.length() <= 300 ? this.body : this.body.substring(0, 300) + "...";
      }
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof HttpResult)) {
         return false;
      } else {
         boolean var10000;
         label36: {
            HttpResult other = (HttpResult)o;
            if (this.statusCode == other.statusCode) {
               if (this.body == null) {
                  if (other.body == null) {
                     break label36;
                  }
               } else if (this.body.equals(other.body)) {
                  break label36;
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
      int result = this.statusCode;
      result = 31 * result + (this.body == null ? 0 : this.body.hashCode());
      return result;
   }

   public String toString() {
      return "HttpResult[statusCode=" + this.statusCode + ", body=" + this.body + "]";
   }
}
