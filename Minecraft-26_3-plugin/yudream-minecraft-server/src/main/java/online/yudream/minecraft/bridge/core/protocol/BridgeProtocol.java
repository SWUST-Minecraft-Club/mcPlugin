package online.yudream.minecraft.bridge.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import online.yudream.minecraft.bridge.core.json.JsonSyntaxException;
import online.yudream.minecraft.bridge.core.json.JsonValue;
import online.yudream.minecraft.bridge.core.model.PlayerIdentity;

public final class BridgeProtocol {
   public static final String CHANNEL_NAMESPACE = "yudream";
   public static final String CHANNEL_PATH = "bridge";
   public static final String CHANNEL = "yudream:bridge";
   public static final int PROTOCOL_VERSION = 1;

   private BridgeProtocol() {
   }

   public static byte[] encode(BridgeMessage message) {
      return message.toJson().toString().getBytes(StandardCharsets.UTF_8);
   }

   public static BridgeMessage decode(byte[] data) {
      if (data != null && data.length != 0) {
         JsonValue root;
         try {
            root = JsonValue.parse(new String(data, StandardCharsets.UTF_8));
         } catch (JsonSyntaxException e) {
            throw new ProtocolException("Malformed bridge payload: " + e.getMessage());
         }

         if (!root.isObject()) {
            throw new ProtocolException("Bridge payload must be a JSON object");
         } else {
            switch (root.getOr("t", JsonValue.of("")).asString("")) {
               case "hello":
                  return readHello(root);
               case "probe":
                  return new BridgeMessage.Probe(root.getOr("proxy", JsonValue.of("")).asString(""), root.getOr("protocol", JsonValue.of(1L)).asInt(1));
               case "hello_ack":
                  return new BridgeMessage.HelloAck(root.getOr("accepting", JsonValue.of(true)).asBoolean(true), root.getOr("target", JsonValue.of("")).asString(""), root.getOr("proxy", JsonValue.of("")).asString(""), root.getOr("protocol", JsonValue.of(1L)).asInt(1));
               case "event":
                  return readEvent(root);
               case "group_msg":
                  return new BridgeMessage.GroupMessage(root.getOr("sender", JsonValue.of("")).asString(""), root.getOr("content", JsonValue.of("")).asString(""), root.getOr("at", JsonValue.of(0L)).asLong(0L));
               default:
                  throw new ProtocolException("Unknown bridge message type '" + root.getOr("t", JsonValue.of("")).asString("") + "'");
            }
         }
      } else {
         throw new ProtocolException("Empty plugin message");
      }
   }

   public static PlayerIdentity readIdentity(JsonValue node) {
      if (node != null && node.isObject()) {
         String id = node.getOr("id", node.getOr("playerId", JsonValue.of(""))).asString("");
         String name = node.getOr("name", node.getOr("playerName", JsonValue.of(""))).asString("");
         if (id.isEmpty()) {
            return null;
         } else {
            try {
               return new PlayerIdentity(UUID.fromString(id), name);
            } catch (IllegalArgumentException var4) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static BridgeMessage readHello(JsonValue root) {
      List<PlayerIdentity> players = new ArrayList();

      for(JsonValue entry : root.getOr("players", JsonValue.array()).items()) {
         PlayerIdentity identity = readIdentity(entry);
         if (identity != null) {
            players.add(identity);
         }
      }

      return new BridgeMessage.Hello(root.getOr("server", JsonValue.of("")).asString(""), root.getOr("mod", JsonValue.of("")).asString(""), root.getOr("protocol", JsonValue.of(0L)).asInt(0), players, root.getOr("at", JsonValue.of(0L)).asLong(0L));
   }

   private static BridgeMessage readEvent(JsonValue root) {
      String kind = root.getOr("kind", JsonValue.of("")).asString("");
      if (kind.isEmpty()) {
         throw new ProtocolException("Bridge event has no kind");
      } else {
         return new BridgeMessage.Event(root.getOr("server", JsonValue.of("")).asString(""), kind, root.has("source") ? root.get("source").asString((String)null) : null, readIdentity(root.get("player")), root.getOr("at", JsonValue.of(0L)).asLong(0L), root.has("content") ? root.get("content").asString((String)null) : null);
      }
   }
}
