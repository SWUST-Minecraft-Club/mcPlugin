package online.yudream.minecraft.bridge.core.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import online.yudream.minecraft.bridge.core.config.BridgeSettings;
import online.yudream.minecraft.bridge.core.json.JsonValue;
import online.yudream.minecraft.bridge.core.model.PlayerEventPayload;
import online.yudream.minecraft.bridge.core.model.PlayerEventType;
import online.yudream.minecraft.bridge.core.model.SubServerInfo;
import online.yudream.minecraft.bridge.core.model.SubServerRoster;

public final class YudreamApiClient {
   static final String PLUGIN_BASE = "/api/plugins/minecraft-server";
   static final String PLUGIN_PATH = "/api/plugins/minecraft-server/servers/";
   static final String REPORT_TOPOLOGY_PATH = "/api/plugins/minecraft-server/report/topology";
   private final BridgeSettings settings;
   private final String userAgent;

   public YudreamApiClient(BridgeSettings settings, String userAgent) {
      this.settings = settings;
      this.userAgent = userAgent;
   }

   public HttpResult report(PlayerEventType type, PlayerEventPayload payload) throws IOException {
      return this.request("POST", this.serverUrl("/" + type.getRemotePath()), this.eventBody(payload));
   }

   public HttpResult snapshot(Collection<PlayerEventPayload> players, long observedAt, String serverName) throws IOException {
      return this.request("POST", this.serverUrl("/players/snapshot"), this.snapshotBody(players, observedAt, serverName));
   }

   public HttpResult groupedSnapshot(Collection<SubServerRoster> servers, long observedAt) throws IOException {
      return this.request("POST", this.serverUrl("/players/snapshot"), this.groupedSnapshotBody(servers, observedAt));
   }

   public HttpResult players(int page, int size) throws IOException {
      String path = "/players?page=" + Math.max(page, 1) + "&size=" + Math.max(Math.min(size, 100), 1);
      return this.request("GET", this.serverUrl(path), (String)null);
   }

   public HttpResult inboundChat(long after) throws IOException {
      String path = "/chat/inbound?after=" + Math.max(after, 0L) + "&limit=50";
      return this.request("GET", this.serverUrl(path), (String)null);
   }

   public HttpURLConnection inboundChatStream(long after) throws IOException {
      String path = "/chat/inbound/stream?after=" + Math.max(after, 0L);
      HttpURLConnection connection = (HttpURLConnection)URI.create(this.serverUrl(path)).toURL().openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(this.settings.getConnectTimeoutMs());
      connection.setReadTimeout(Math.max(this.settings.getReadTimeoutMs(), 90000));
      connection.setRequestProperty("Accept", "text/event-stream");
      connection.setRequestProperty("Cache-Control", "no-cache");
      connection.setRequestProperty("User-Agent", this.userAgent);
      connection.setRequestProperty("X-API-Key", this.settings.getApiKey());
      return connection;
   }

   public HttpResult reportTopology(String body) throws IOException {
      return this.request("POST", this.serverUrl("/topology"), body);
   }

   public HttpResult reportTopologyByAddress(String body) throws IOException {
      return this.request("POST", this.settings.getBaseUrl() + "/api/plugins/minecraft-server/report/topology", body);
   }

   public String topologyBody(String proxy, String proxyVersion, Collection<String> addresses, Collection<SubServerInfo> servers, long reportedAt) {
      JsonValue body = JsonValue.object();
      body.put("proxy", proxy == null ? "" : proxy);
      body.put("proxyVersion", proxyVersion == null ? "" : proxyVersion);
      body.put("reportedAt", reportedAt);
      JsonValue addressList = JsonValue.array();
      if (addresses != null) {
         for(String address : addresses) {
            if (address != null && !address.isEmpty()) {
               addressList.add(JsonValue.of(address));
            }
         }
      }

      body.put("addresses", addressList);
      JsonValue serverList = JsonValue.array();
      if (servers != null) {
         for(SubServerInfo server : servers) {
            serverList.add(JsonValue.object().put("name", server.name()).put("address", server.address() == null ? "" : server.address()).put("online", (long)server.online()).put("sensor", server.sensor()).put("defaultServer", server.defaultServer()));
         }
      }

      body.put("servers", serverList);
      return body.toString();
   }

   public String serverUrl(String serverRelativePath) {
      return this.settings.getBaseUrl() + "/api/plugins/minecraft-server/servers/" + encodePathSegment(this.settings.getServerId()) + serverRelativePath;
   }

   public String eventBody(PlayerEventPayload payload) {
      JsonValue body = JsonValue.object().put("playerId", payload.playerId()).put("playerName", payload.playerName()).put("eventAt", payload.eventAt());
      if (payload.serverName() != null && !payload.serverName().isEmpty()) {
         body.put("server", payload.serverName());
      }

      if (payload.content() != null && !payload.content().isEmpty()) {
         body.put("content", payload.content());
      }

      return body.toString();
   }

   public String snapshotBody(Collection<PlayerEventPayload> players, long observedAt, String serverName) {
      JsonValue body = JsonValue.object();
      body.put("observedAt", observedAt);
      if (this.settings.isIncludeServerNameInSnapshot() && serverName != null && !serverName.isEmpty()) {
         body.put("serverName", serverName);
      }

      body.put("players", playerArray(players));
      return body.toString();
   }

   public String groupedSnapshotBody(Collection<SubServerRoster> servers, long observedAt) {
      JsonValue body = JsonValue.object();
      body.put("observedAt", observedAt);
      JsonValue list = JsonValue.array();
      if (servers != null) {
         for(SubServerRoster roster : servers) {
            list.add(JsonValue.object().put("name", roster.name() == null ? "" : roster.name()).put("players", playerArray(roster.players())));
         }
      }

      body.put("servers", list);
      return body.toString();
   }

   private static JsonValue playerArray(Collection<PlayerEventPayload> players) {
      JsonValue list = JsonValue.array();
      if (players == null) {
         return list;
      } else {
         for(PlayerEventPayload player : players) {
            list.add(JsonValue.object().put("playerId", player.playerId()).put("playerName", player.playerName()));
         }

         return list;
      }
   }

   private HttpResult request(String method, String url, String body) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection();

      HttpResult var7;
      try {
         connection.setRequestMethod(method);
         connection.setConnectTimeout(this.settings.getConnectTimeoutMs());
         connection.setReadTimeout(this.settings.getReadTimeoutMs());
         connection.setRequestProperty("Accept", "application/json");
         connection.setRequestProperty("User-Agent", this.userAgent);
         connection.setRequestProperty("X-API-Key", this.settings.getApiKey());
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
         var7 = new HttpResult(status, responseBody);
      } finally {
         connection.disconnect();
      }

      return var7;
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
      } catch (UnsupportedEncodingException e) {
         throw new IllegalStateException("UTF-8 is unavailable", e);
      }
   }
}
