package online.yudream.minecraft.bukkit.bridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import online.yudream.minecraft.bridge.core.http.InboundChatSse;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.api.HttpResult;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import org.bukkit.Bukkit;

public final class InboundChatPoller {
   private static final int LOG_THROTTLE_MS = 60000;
   private final YudreamMinecraftPlugin plugin;
   private final AtomicBoolean streaming = new AtomicBoolean(false);
   private long cursor;
   private long lastFailureLogAt;
   private long lastConnectedLogAt;
   private long lastCrashLogAt;
   private boolean loggedGateSkip;

   public InboundChatPoller(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
   }

   private void debugLog(String message) {
      if (this.plugin.getSettings().getChatBridge().isDebug()) {
         this.plugin.getLogger().info("[群服互联调试] " + message);
      }

   }

   public void poll() {
      try {
         this.pollUnsafe();
      } catch (Throwable error) {
         long now = System.currentTimeMillis();
         if (now - this.lastCrashLogAt >= 60000L) {
            this.lastCrashLogAt = now;
            this.plugin.getLogger().log(Level.SEVERE, "群服互联入站任务异常（已捕获，任务继续运行）", error);
         }
      }

   }

   private void pollUnsafe() {
      this.debugLog("入站 tick：canReport=" + this.plugin.canReport() + " cursor=" + this.cursor + " streaming=" + this.streaming.get());
      if (!this.plugin.canReport()) {
         if (!this.loggedGateSkip) {
            this.loggedGateSkip = true;
            this.plugin.getLogger().info("群服互联入站跳过：本机无上报凭据或处于下游代理模式（由代理统一拉取）。");
         }

      } else {
         this.loggedGateSkip = false;
         if (!this.streaming.compareAndSet(false, true)) {
            this.debugLog("入站 tick：已有 SSE 流在读，本 tick 跳过");
         } else {
            try {
               this.streamOnce();
               return;
            } catch (IOException streamFailure) {
               this.logFailure("实时推送不可用，退回轮询：" + streamFailure.getMessage());
               this.pollOnce();
            } finally {
               this.streaming.set(false);
            }

         }
      }
   }

   private void streamOnce() throws IOException {
      this.debugLog("SSE 连接中：after=" + this.cursor + "（完整 URL 见 debug 首行）");
      InboundChatSse.read(this.plugin.getApiClient().inboundChatStream(this.cursor), new InboundChatSse.Listener() {
         public void onEvent(String event, String data) {
            InboundChatPoller.this.debugLog("SSE 收到事件 event=" + event + " data=" + InboundChatPoller.abbreviate(data));
         }

         public void onConnected(long latest) {
            InboundChatPoller.this.logConnected(latest);
            if (latest > 0L && latest < InboundChatPoller.this.cursor) {
               InboundChatPoller.this.debugLog("服务端序号回绕：latest=" + latest + " < cursor=" + InboundChatPoller.this.cursor + "，游标重置为 " + latest);
               InboundChatPoller.this.cursor = latest;
            }

            if (InboundChatPoller.this.cursor == 0L && latest > 0L) {
               InboundChatPoller.this.debugLog("SSE 首连快进：cursor 0 -> " + latest + "（不重播历史消息）");
               InboundChatPoller.this.cursor = latest;
            }

         }

         public void onMessage(long seq, String sender, String content, long at) {
            InboundChatPoller.this.debugLog("SSE 收到消息帧 seq=" + seq + " sender=" + sender + " content=" + InboundChatPoller.abbreviate(content) + "（cursor=" + InboundChatPoller.this.cursor + "）");
            if (seq > InboundChatPoller.this.cursor && !content.trim().isEmpty()) {
               InboundChatPoller.this.cursor = seq;
               InboundChatPoller.this.plugin.getLogger().info("已转发群消息 #" + seq + " " + sender + "：" + InboundChatPoller.abbreviate(content));
               InboundChatPoller.this.broadcast(sender, content);
            } else {
               InboundChatPoller.this.debugLog("跳过消息 seq=" + seq + "（≤ cursor=" + InboundChatPoller.this.cursor + " 或内容为空）");
            }
         }
      });
      this.debugLog("SSE 流被服务端关闭（EOF）");
   }

   private void pollOnce() {
      HttpResult result;
      try {
         result = this.plugin.getApiClient().inboundChat(this.cursor);
      } catch (IOException e) {
         this.logFailure("连接 YuDream Admin 失败：" + e.getMessage());
         return;
      }

      if (!result.isSuccess()) {
         this.logFailure("拉取群消息失败，HTTP " + result.getStatusCode());
      } else {
         Map<String, Object> body = MiniJson.parseObject(result.getBody());
         if (body != null) {
            long latest = MiniJson.longValue(body, "latest", this.cursor);
            boolean fastForward = this.cursor == 0L && latest > 0L;
            this.debugLog("轮询响应：latest=" + latest + " fastForward=" + fastForward + "（cursor=" + this.cursor + "）");
            this.cursor = Math.max(this.cursor, latest);
            Object rawMessages = body.get("messages");
            if (rawMessages instanceof List && !fastForward) {
               final List<Broadcast> broadcasts = new ArrayList();

               for(Object item : (List)rawMessages) {
                  if (item instanceof Map) {
                     Map<?, ?> message = (Map)item;
                     Object content = message.get("content");
                     if (content != null && !String.valueOf(content).trim().isEmpty()) {
                        long seq = message.get("seq") instanceof Number ? ((Number)message.get("seq")).longValue() : 0L;
                        String sender = String.valueOf(message.get("sender"));
                        broadcasts.add(new Broadcast(seq, sender, String.valueOf(content)));
                        this.cursor = Math.max(this.cursor, seq);
                        this.plugin.getLogger().info("已转发群消息 #" + seq + " " + sender + "：" + abbreviate(String.valueOf(content)));
                     }
                  }
               }

               if (!broadcasts.isEmpty()) {
                  Runnable deliver = new Runnable() {
                     public void run() {
                        for(Broadcast broadcast : broadcasts) {
                           String line = InboundChatPoller.this.plugin.getSettings().getChatBridge().formatInbound(broadcast.sender, broadcast.content);
                           Bukkit.broadcastMessage(line);
                        }

                     }
                  };
                  FoliaSchedulerCompat.runGlobal(this.plugin, deliver);
               }
            }
         }
      }
   }

   private void broadcast(final String sender, final String content) {
      FoliaSchedulerCompat.runGlobal(this.plugin, new Runnable() {
         public void run() {
            String line = InboundChatPoller.this.plugin.getSettings().getChatBridge().formatInbound(sender, content);
            Bukkit.broadcastMessage(line);
         }
      });
   }

   private void logConnected(long latest) {
      long now = System.currentTimeMillis();
      if (now - this.lastConnectedLogAt >= 60000L) {
         this.lastConnectedLogAt = now;
         this.plugin.getLogger().info("群聊实时推送已连接（服务端最新序号 " + latest + "），群消息将实时转发进游戏。");
      }
   }

   private void logFailure(String message) {
      long now = System.currentTimeMillis();
      if (now - this.lastFailureLogAt >= 60000L) {
         this.lastFailureLogAt = now;
         this.plugin.getLogger().log(Level.WARNING, "群服互联入站拉取：" + message);
      }
   }

   private static String abbreviate(String content) {
      String flat = content.replace("\n", " ").trim();
      return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
   }

   private static final class Broadcast {
      private final long seq;
      private final String sender;
      private final String content;

      private Broadcast(long seq, String sender, String content) {
         this.seq = seq;
         this.sender = sender;
         this.content = content;
      }
   }
}
