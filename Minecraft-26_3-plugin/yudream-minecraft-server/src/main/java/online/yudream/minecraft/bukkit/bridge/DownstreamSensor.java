package online.yudream.minecraft.bukkit.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import online.yudream.minecraft.bukkit.config.DownstreamOptions;
import online.yudream.minecraft.bukkit.report.PlayerEventPayload;
import online.yudream.minecraft.bukkit.report.PlayerEventType;
import online.yudream.minecraft.bukkit.util.PlayerSnapshots;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class DownstreamSensor implements PluginMessageListener {
   private final YudreamMinecraftPlugin plugin;
   private final DownstreamOptions options;
   private final Map<UUID, Long> lastActivityAt = new ConcurrentHashMap();
   private volatile long lastAckAt;
   private volatile int proxyProtocolVersion = -1;
   private volatile String proxyVersion = "";
   private volatile String proxyTarget = "";
   private volatile boolean proxyAccepting;
   private Object heartbeatTask;
   private boolean channelsRegistered;

   public DownstreamSensor(YudreamMinecraftPlugin plugin, DownstreamOptions options) {
      this.plugin = plugin;
      this.options = options;
   }

   public void start() {
      if (!this.channelsRegistered) {
         Messenger messenger = this.plugin.getServer().getMessenger();
         messenger.registerOutgoingPluginChannel(this.plugin, "yudream:bridge");
         messenger.registerIncomingPluginChannel(this.plugin, "yudream:bridge", this);
         this.channelsRegistered = true;
      }

      long periodTicks = Math.max(this.options.getHeartbeatSeconds(), 1L) * 20L;
      if (this.heartbeatTask == null) {
         this.heartbeatTask = FoliaSchedulerCompat.runGlobalRepeating(this.plugin, new Runnable() {
            public void run() {
               DownstreamSensor.this.sendHello((Player)null);
            }
         }, periodTicks, periodTicks);
      }

      this.plugin.getLogger().info("Downstream mode active. Player activity is forwarded to the proxy on yudream:bridge; this server does not contact YuDream Admin directly" + (this.options.isFallbackToApi() ? " unless the proxy stays silent." : "."));
   }

   public void stop() {
      if (this.heartbeatTask != null) {
         FoliaSchedulerCompat.cancel(this.heartbeatTask);
         this.heartbeatTask = null;
      }

      if (this.channelsRegistered) {
         Messenger messenger = this.plugin.getServer().getMessenger();
         messenger.unregisterOutgoingPluginChannel(this.plugin, "yudream:bridge");
         messenger.unregisterIncomingPluginChannel(this.plugin, "yudream:bridge", this);
         this.channelsRegistered = false;
      }

      this.lastActivityAt.clear();
   }

   public void onPlayerJoin(Player player) {
      this.sendHello(player);
      this.sendEvent(player, "join", (String)null);
   }

   public void onPlayerQuit(Player player) {
      this.lastActivityAt.remove(player.getUniqueId());
      this.sendEvent(player, "quit", (String)null);
   }

   public void onActivity(Player player, String source) {
      long now = System.currentTimeMillis();
      Long previous = (Long)this.lastActivityAt.get(player.getUniqueId());
      if (previous == null || now - previous >= this.options.getActivityMinIntervalMs()) {
         this.lastActivityAt.put(player.getUniqueId(), now);
         this.sendEvent(player, "activity", source);
      }
   }

   public void onGameEvent(PlayerEventType type, PlayerEventPayload payload) {
      String kind;
      if (type == PlayerEventType.CHAT) {
         kind = "chat";
      } else if (type == PlayerEventType.DEATH) {
         kind = "death";
      } else {
         if (type != PlayerEventType.ADVANCEMENT) {
            return;
         }

         kind = "advancement";
      }

      Player carrier = this.firstOnlinePlayer();
      if (carrier != null) {
         this.send(carrier, BridgeProtocol.encodeEvent(kind, (String)null, payload, payload.getEventAt()));
      }
   }

   public void requestHello() {
      this.sendHello((Player)null);
   }

   private void sendHello(Player carrier) {
      Player chosen = carrier != null && carrier.isOnline() ? carrier : this.firstOnlinePlayer();
      if (chosen != null) {
         Collection<? extends Player> online = this.plugin.getServer().getOnlinePlayers();
         List<PlayerEventPayload> players = new ArrayList(online.size());

         for(Player player : online) {
            players.add(PlayerSnapshots.from(player));
         }

         this.send(chosen, BridgeProtocol.encodeHello(this.plugin.getDescription().getVersion(), players, System.currentTimeMillis()));
      }
   }

   private void sendEvent(Player player, String kind, String source) {
      if (player != null && player.isOnline()) {
         this.send(player, BridgeProtocol.encodeEvent(kind, source, PlayerSnapshots.from(player), System.currentTimeMillis()));
      }
   }

   private void send(final Player player, final byte[] data) {
      try {
         // 插件消息必须在该玩家的区域线程发送；onPlayerQuit 等已经在归属线程时会直接执行
         FoliaSchedulerCompat.runFor(this.plugin, player, new Runnable() {
            public void run() {
               if (player.isOnline()) {
                  player.sendPluginMessage(DownstreamSensor.this.plugin, "yudream:bridge", data);
               }
            }
         });
      } catch (Throwable t) {
         this.plugin.getLogger().log(Level.FINE, "Could not forward a YuDream bridge message", t);
      }

   }

   private Player firstOnlinePlayer() {
      Collection<? extends Player> online = this.plugin.getServer().getOnlinePlayers();
      Iterator var2 = online.iterator();
      if (var2.hasNext()) {
         Player player = (Player)var2.next();
         return player;
      } else {
         return null;
      }
   }

   public void onPluginMessageReceived(String channel, final Player player, byte[] message) {
      if ("yudream:bridge".equals(channel)) {
         final BridgeProtocol.Incoming incoming = BridgeProtocol.decode(message);
         if (incoming != null) {
            // 插件消息回调在 Netty 线程；回到该玩家的区域线程再处理
            FoliaSchedulerCompat.runFor(this.plugin, player, new Runnable() {
               public void run() {
                  if (player.isOnline()) {
                     if (incoming.isProbe()) {
                        DownstreamSensor.this.sendHello(player);
                     } else if (incoming.isHelloAck()) {
                        DownstreamSensor.this.onHelloAck(incoming);
                     } else if (incoming.isGroupMessage()) {
                        DownstreamSensor.this.broadcastGroupMessage(incoming);
                     }

                  }
               }
            });
         }
      }
   }

   private void broadcastGroupMessage(final BridgeProtocol.Incoming incoming) {
      final String content = incoming.getContent();
      if (content != null && !content.trim().isEmpty()) {
         final String line = this.plugin.getSettings().getChatBridge().formatInbound(incoming.getSender(), content);
         FoliaSchedulerCompat.runGlobal(this.plugin, new Runnable() {
            public void run() {
               DownstreamSensor.this.plugin.getServer().broadcastMessage(line);
            }
         });
      }
   }

   private void onHelloAck(BridgeProtocol.Incoming incoming) {
      boolean first = this.lastAckAt == 0L;
      this.lastAckAt = System.currentTimeMillis();
      this.proxyProtocolVersion = incoming.getProtocolVersion();
      this.proxyVersion = incoming.getProxyVersion();
      this.proxyTarget = incoming.getTargetServer();
      this.proxyAccepting = incoming.isAccepting();
      if (first) {
         if (this.proxyProtocolVersion != 1) {
            this.plugin.getLogger().warning("The proxy speaks YuDream bridge protocol " + this.proxyProtocolVersion + " but this plugin speaks " + 1 + ". Update the older side; no reports will be taken from this server.");
         } else {
            this.plugin.getLogger().info("Proxy bridge acknowledged: proxy " + this.proxyVersion + ", reporting downstream server " + (this.proxyTarget.isEmpty() ? "<unset>" : this.proxyTarget) + ".");
         }
      }

   }

   public boolean isProxyReachable() {
      long ack = this.lastAckAt;
      return ack > 0L && System.currentTimeMillis() - ack <= this.options.getAckTimeoutMs();
   }

   public long getLastAckAt() {
      return this.lastAckAt;
   }

   public String describeLink() {
      long ack = this.lastAckAt;
      return ack <= 0L ? "no reply from the proxy yet (channel yudream:bridge)" : "proxy " + (this.proxyVersion.isEmpty() ? "?" : this.proxyVersion) + ", protocol " + this.proxyProtocolVersion + ", accepting " + this.proxyAccepting + ", reporting downstream " + (this.proxyTarget.isEmpty() ? "<unset>" : this.proxyTarget) + ", lastReplyMsAgo " + (System.currentTimeMillis() - ack);
   }
}
