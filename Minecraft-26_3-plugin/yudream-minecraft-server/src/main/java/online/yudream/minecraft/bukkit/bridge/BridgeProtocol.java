package online.yudream.minecraft.bukkit.bridge;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import online.yudream.minecraft.bukkit.report.PlayerEventPayload;
import online.yudream.minecraft.bukkit.util.JsonObjects;

public final class BridgeProtocol {
   public static final String CHANNEL = "yudream:bridge";
   public static final int PROTOCOL_VERSION = 1;
   public static final String TYPE_HELLO = "hello";
   public static final String TYPE_HELLO_ACK = "hello_ack";
   public static final String TYPE_PROBE = "probe";
   public static final String TYPE_EVENT = "event";
   public static final String TYPE_GROUP_MSG = "group_msg";
   public static final String KIND_JOIN = "join";
   public static final String KIND_QUIT = "quit";
   public static final String KIND_ACTIVITY = "activity";
   public static final String KIND_CHAT = "chat";
   public static final String KIND_DEATH = "death";
   public static final String KIND_ADVANCEMENT = "advancement";
   public static final String SOURCE_CHAT = "chat";
   public static final String SOURCE_MOVE = "move";
   public static final String SOURCE_INTERACT = "interact";
   public static final String SOURCE_COMMAND = "command";
   private static final Charset UTF_8 = Charset.forName("UTF-8");

   private BridgeProtocol() {
   }

   public static byte[] encodeHello(String modVersion, Collection<PlayerEventPayload> players, long at) {
      StringBuilder json = new StringBuilder(128);
      json.append("{\"t\":\"").append("hello").append('"');
      json.append(",\"protocol\":").append(1);
      json.append(",\"mod\":\"").append(JsonObjects.escape(modVersion)).append('"');
      json.append(",\"server\":\"\"");
      json.append(",\"at\":").append(at);
      json.append(",\"players\":[");
      boolean first = true;

      for(PlayerEventPayload player : players) {
         if (!first) {
            json.append(',');
         }

         first = false;
         json.append("{\"id\":\"").append(JsonObjects.escape(player.getPlayerId())).append("\",\"name\":\"").append(JsonObjects.escape(player.getPlayerName())).append("\"}");
      }

      return bytes(json.append("]}").toString());
   }

   public static byte[] encodeEvent(String kind, String source, PlayerEventPayload player, long at) {
      return encodeEvent(kind, source, player, at, player == null ? null : player.getContent());
   }

   public static byte[] encodeEvent(String kind, String source, PlayerEventPayload player, long at, String content) {
      StringBuilder json = new StringBuilder(128);
      json.append("{\"t\":\"").append("event").append('"');
      json.append(",\"server\":\"\"");
      json.append(",\"kind\":\"").append(JsonObjects.escape(kind)).append('"');
      json.append(",\"at\":").append(at);
      if (source != null && !source.isEmpty()) {
         json.append(",\"source\":\"").append(JsonObjects.escape(source)).append('"');
      }

      if (content != null && !content.isEmpty()) {
         json.append(",\"content\":\"").append(JsonObjects.escape(content)).append('"');
      }

      if (player != null) {
         json.append(",\"player\":{\"id\":\"").append(JsonObjects.escape(player.getPlayerId())).append("\",\"name\":\"").append(JsonObjects.escape(player.getPlayerName())).append("\"}");
      }

      return bytes(json.append('}').toString());
   }

   public static byte[] encodeGroupMessage(String sender, String content, long at) {
      StringBuilder json = new StringBuilder(64);
      json.append("{\"t\":\"").append("group_msg").append('"');
      json.append(",\"protocol\":").append(1);
      json.append(",\"sender\":\"").append(JsonObjects.escape(sender)).append('"');
      json.append(",\"content\":\"").append(JsonObjects.escape(content)).append('"');
      json.append(",\"at\":").append(at);
      return bytes(json.append('}').toString());
   }

   public static Incoming decode(byte[] data) {
      if (data != null && data.length != 0) {
         Map<String, Object> root = MiniJson.parseObject(new String(data, UTF_8));
         if (root == null) {
            return null;
         } else {
            String type = MiniJson.string(root, "t", "");
            return !"probe".equals(type) && !"hello_ack".equals(type) && !"group_msg".equals(type) ? null : new Incoming(type, MiniJson.integer(root, "protocol", 0), MiniJson.string(root, "proxy", ""), MiniJson.bool(root, "accepting", true), MiniJson.string(root, "target", ""), MiniJson.string(root, "sender", ""), MiniJson.string(root, "content", ""));
         }
      } else {
         return null;
      }
   }

   private static byte[] bytes(String text) {
      try {
         return text.getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
         throw new IllegalStateException("UTF-8 is unavailable", e);
      }
   }

   public static final class Incoming {
      private final String type;
      private final int protocolVersion;
      private final String proxyVersion;
      private final boolean accepting;
      private final String targetServer;
      private final String sender;
      private final String content;

      private Incoming(String type, int protocolVersion, String proxyVersion, boolean accepting, String targetServer, String sender, String content) {
         this.type = type;
         this.protocolVersion = protocolVersion;
         this.proxyVersion = proxyVersion;
         this.accepting = accepting;
         this.targetServer = targetServer;
         this.sender = sender;
         this.content = content;
      }

      public String getType() {
         return this.type;
      }

      public int getProtocolVersion() {
         return this.protocolVersion;
      }

      public String getProxyVersion() {
         return this.proxyVersion;
      }

      public boolean isAccepting() {
         return this.accepting;
      }

      public String getTargetServer() {
         return this.targetServer;
      }

      public String getSender() {
         return this.sender;
      }

      public String getContent() {
         return this.content;
      }

      public boolean isProbe() {
         return "probe".equals(this.type);
      }

      public boolean isHelloAck() {
         return "hello_ack".equals(this.type);
      }

      public boolean isGroupMessage() {
         return "group_msg".equals(this.type);
      }
   }
}
