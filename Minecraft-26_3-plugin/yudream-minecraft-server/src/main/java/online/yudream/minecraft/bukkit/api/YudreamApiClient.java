package online.yudream.minecraft.bukkit.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import online.yudream.minecraft.bukkit.config.YudreamConfig;
import online.yudream.minecraft.bukkit.report.PlayerEventPayload;
import online.yudream.minecraft.bukkit.report.PlayerEventType;
import online.yudream.minecraft.bukkit.util.JsonObjects;

public final class YudreamApiClient {
   private static final String PLUGIN_PATH = "/api/plugins/minecraft-server/servers/";
   private final YudreamConfig config;

   public YudreamApiClient(YudreamConfig config) {
      this.config = config;
   }

   public HttpResult report(PlayerEventType type, PlayerEventPayload payload) throws IOException {
      return this.request("POST", this.serverUrl("/" + type.getRemotePath()), JsonObjects.playerEvent(payload));
   }

   public HttpResult snapshot(Collection<PlayerEventPayload> players, long observedAt) throws IOException {
      return this.request("POST", this.serverUrl("/players/snapshot"), JsonObjects.playerSnapshot(players, observedAt));
   }

   /**
    * 玩家活动列表（服务端侧用来把本地时长台账与后端对齐，见 {@code playtime.PlaytimeSync}）。
    *
    * <p>管理端路径 {@code /admin/servers/{id}/players} 是新后端的正式位置（前端也用它），
    * 需要 API key 带 {@code plugin:minecraft-server:manage} 权限；部分部署上它可能返回 5xx，
    * 调用方（{@code PlaytimeSync}）会在失败时换另一条路径，所以这里把两条路径都开放出来。
    */
   public HttpResult players(int page, int size) throws IOException {
      return this.playersAt(true, page, size);
   }

   /** 取指定路径的玩家活动列表：{@code adminPath} 为 true 走管理端，false 走用户端旧路径。 */
   public HttpResult playersAt(boolean adminPath, int page, int size) throws IOException {
      String query = "/players?page=" + Math.max(page, 1) + "&size=" + Math.max(Math.min(size, 100), 1);
      String url = adminPath ? this.adminServerUrl(query) : this.serverUrl(query);
      return this.request("GET", url, (String)null);
   }

   public HttpResult inboundChat(long after) throws IOException {
      String path = "/chat/inbound?after=" + Math.max(after, 0L) + "&limit=50";
      return this.request("GET", this.serverUrl(path), (String)null);
   }

   public HttpURLConnection inboundChatStream(long after) throws IOException {
      String path = "/chat/inbound/stream?after=" + Math.max(after, 0L);
      HttpURLConnection connection = (HttpURLConnection)URI.create(this.serverUrl(path)).toURL().openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(this.config.getConnectTimeoutMs());
      connection.setReadTimeout(Math.max(this.config.getReadTimeoutMs(), 90000));
      connection.setRequestProperty("Accept", "text/event-stream");
      connection.setRequestProperty("Cache-Control", "no-cache");
      connection.setRequestProperty("User-Agent", "YudreamMinecraftServerBukkit/1.0");
      connection.setRequestProperty("X-API-Key", this.config.getApiKey());
      return connection;
   }

   String serverUrl(String serverRelativePath) {
      return this.config.getBaseUrl() + "/api/plugins/minecraft-server/servers/" + encodePathSegment(this.config.getServerId()) + serverRelativePath;
   }

   /** 管理端路径（前缀里多一段 admin）。 */
   String adminServerUrl(String serverRelativePath) {
      return this.config.getBaseUrl() + "/api/plugins/minecraft-server/admin/servers/" + encodePathSegment(this.config.getServerId()) + serverRelativePath;
   }

   private HttpResult request(String method, String url, String body) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(this.config.getConnectTimeoutMs());
      connection.setReadTimeout(this.config.getReadTimeoutMs());
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "YudreamMinecraftServerBukkit/1.0");
      connection.setRequestProperty("X-API-Key", this.config.getApiKey());
      if (body != null) {
         byte[] data = body.getBytes(StandardCharsets.UTF_8);
         connection.setDoOutput(true);
         connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
         connection.setFixedLengthStreamingMode(data.length);
         OutputStream out = connection.getOutputStream();

         try {
            out.write(data);
         } finally {
            out.close();
         }
      }

      int status = connection.getResponseCode();
      String responseBody = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
      connection.disconnect();
      return new HttpResult(status, responseBody);
   }

   private static String readBody(InputStream stream) throws IOException {
      if (stream == null) {
         return "";
      } else {
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
         StringBuilder body = new StringBuilder();

         String var4;
         try {
            String line;
            for(; (line = reader.readLine()) != null; body.append(line)) {
               if (body.length() > 0) {
                  body.append('\n');
               }
            }

            var4 = body.toString();
         } finally {
            reader.close();
         }

         return var4;
      }
   }

   static String encodePathSegment(String value) {
      try {
         return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
      } catch (IOException e) {
         throw new IllegalStateException("UTF-8 is unavailable", e);
      }
   }
}
