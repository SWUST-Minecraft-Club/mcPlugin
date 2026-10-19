package online.yudream.minecraft.bridge.core.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import online.yudream.minecraft.bridge.core.json.JsonSyntaxException;
import online.yudream.minecraft.bridge.core.json.JsonValue;

public final class InboundChatSse {
   private InboundChatSse() {
   }

   public static void read(HttpURLConnection connection, Listener listener) throws IOException {
      try {
         int status = connection.getResponseCode();
         if (status != 200) {
            throw new IOException("HTTP " + status);
         }

         String contentType = connection.getContentType();
         if (contentType == null || !contentType.contains("text/event-stream")) {
            throw new IOException("非 SSE 响应：" + contentType);
         }

         BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
         String event = "message";
         StringBuilder data = null;

         String line;
         while((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
               if (data != null) {
                  dispatch(event, data.toString(), listener);
               }

               event = "message";
               data = null;
            } else if (line.startsWith("event:")) {
               event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
               String payload = line.substring(5);
               if (payload.startsWith(" ")) {
                  payload = payload.substring(1);
               }

               if (data == null) {
                  data = new StringBuilder();
               }

               if (data.length() > 0) {
                  data.append('\n');
               }

               data.append(payload);
            }
         }
      } finally {
         connection.disconnect();
      }

   }

   private static void dispatch(String event, String data, Listener listener) {
      listener.onEvent(event, data);
      if ("connected".equals(event)) {
         JsonValue value = parse(data);
         if (value != null && value.isObject()) {
            listener.onConnected(value.getOr("latest", JsonValue.of(0L)).asLong(0L));
         }

      } else if ("message".equals(event)) {
         JsonValue value = parse(data);
         if (value != null && value.isObject()) {
            String content = value.getOr("content", JsonValue.of("")).asString("");
            if (!content.trim().isEmpty()) {
               listener.onMessage(value.getOr("seq", JsonValue.of(0L)).asLong(0L), value.getOr("sender", JsonValue.of("")).asString(""), content, value.getOr("at", JsonValue.of(0L)).asLong(0L));
            }
         }
      }
   }

   private static JsonValue parse(String data) {
      try {
         return JsonValue.parse(data);
      } catch (JsonSyntaxException var2) {
         return null;
      }
   }

   public interface Listener {
      default void onConnected(long latest) {
      }

      default void onEvent(String event, String data) {
      }

      void onMessage(long var1, String var3, String var4, long var5);
   }
}
