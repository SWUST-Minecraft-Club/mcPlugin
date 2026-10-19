package online.yudream.minecraft.bukkit.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import online.yudream.minecraft.bukkit.bridge.MiniJson;
import online.yudream.minecraft.bukkit.config.DurableQueueOptions;
import online.yudream.minecraft.bukkit.util.JsonObjects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 待发上报的落盘日志（JSON Lines，一行一条）。
 *
 * <p>只记录会影响在线时长的「事件型」上报（进服 / 退服 / AFK 起止）：后端不可用、未配置或网络失败时
 * 先写进这里，后端恢复后由 {@link ReportQueue} 按原始时间戳按序补发；进程重启也不丢。
 * 聊天 / 死亡 / 成就这类消息不落盘（补发过期消息没有意义）。
 *
 * <p>后端是按事件的 {@code eventAt} 结算的，而 join / quit / AFK 都是幂等的
 * （重复上线不重置起点、重复下线不再结算一次），所以补发不会造成重复计时。
 *
 * <p>文件：插件数据目录下的 {@code pending-reports.jsonl}；每行形如
 * <pre>{"id":1,"type":"JOIN","playerId":"...","playerName":"...","eventAt":1710000000000,"content":null}</pre>
 */
public final class PendingReportJournal {

   private static final String FILE_NAME = "pending-reports.jsonl";

   private final JavaPlugin plugin;
   private final File file;
   private final int maxPending;
   private final Map<Long, Pending> pending = new LinkedHashMap<>();
   private long nextId = 1L;

   public PendingReportJournal(JavaPlugin plugin, DurableQueueOptions options) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), FILE_NAME);
      this.maxPending = options.getMaxPending();
      PendingReportJournal loaded = this;
      this.load();
   }

   public File getFile() {
      return this.file;
   }

   public synchronized int size() {
      return this.pending.size();
   }

   /** 追加一条待发上报，返回它的编号（后端确认成功后用 {@link #remove(long)} 删掉）。 */
   public synchronized long append(PlayerEventType type, PlayerEventPayload payload) {
      long id = this.nextId++;
      Pending entry = new Pending(id, type, payload.getPlayerId(), payload.getPlayerName(),
              payload.getEventAt(), payload.getContent());
      this.pending.put(Long.valueOf(id), entry);
      this.trim();
      this.flush();
      return id;
   }

   /** 取最老的一条待发上报（没有则返回 {@code null}）。 */
   public synchronized Pending peek() {
      for (Pending entry : this.pending.values()) {
         return entry;
      }

      return null;
   }

   /** 后端确认成功后删除一条。 */
   public synchronized void remove(long id) {
      if (this.pending.remove(Long.valueOf(id)) != null) {
         this.flush();
      }
   }

   public synchronized List<Pending> snapshot() {
      return Collections.unmodifiableList(new ArrayList<>(this.pending.values()));
   }

   private void trim() {
      while (this.pending.size() > this.maxPending) {
         Long oldest = null;
         for (Long key : this.pending.keySet()) {
            oldest = key;
            break;
         }

         if (oldest == null) {
            return;
         }

         Pending dropped = this.pending.remove(oldest);
         if (dropped != null) {
            this.plugin.getLogger().warning("YuDream pending report journal is full ("
                    + this.maxPending + "); dropped the oldest " + dropped.type + " report for "
                    + dropped.playerName + " (" + dropped.playerId + ")");
         }
      }
   }

   private void load() {
      if (!this.file.isFile()) {
         return;
      }

      int skipped = 0;
      try {
         for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
               continue;
            }

            Pending entry = parse(trimmed);
            if (entry == null) {
               skipped++;
               continue;
            }

            this.pending.put(Long.valueOf(entry.id), entry);
            if (entry.id >= this.nextId) {
               this.nextId = entry.id + 1L;
            }
         }
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.WARNING, "Could not read the YuDream pending report journal at " + this.file, e);
         return;
      }

      if (!this.pending.isEmpty() || skipped > 0) {
         this.plugin.getLogger().info("Recovered " + this.pending.size()
                 + " pending YuDream report(s) from " + this.file.getName()
                 + (skipped > 0 ? " (" + skipped + " unusable line(s) dropped)" : "")
                 + "；后端恢复后会自动补发。");
      }
   }

   private static Pending parse(String line) {
      Map<String, Object> object;
      try {
         object = MiniJson.parseObject(line);
      } catch (RuntimeException e) {
         return null;
      }

      if (object == null) {
         return null;
      }

      String typeName = MiniJson.string(object, "type", "");
      PlayerEventType type;
      try {
         type = PlayerEventType.valueOf(typeName);
      } catch (IllegalArgumentException e) {
         return null;
      }

      return new Pending(MiniJson.longValue(object, "id", 0L), type,
              MiniJson.string(object, "playerId", ""), MiniJson.string(object, "playerName", ""),
              MiniJson.longValue(object, "eventAt", 0L), MiniJson.string(object, "content", null));
   }

   private void flush() {
      Path target = this.file.toPath();
      Path parent = target.getParent();
      StringBuilder out = new StringBuilder();

      for (Pending entry : this.pending.values()) {
         out.append(entry.toJson()).append('\n');
      }

      try {
         if (parent != null) {
            Files.createDirectories(parent);
         }

         Path temp = parent == null ? target.resolveSibling(FILE_NAME + ".tmp")
                 : parent.resolve(FILE_NAME + ".tmp");
         Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8));
         try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomicUnsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.WARNING, "Could not write the YuDream pending report journal at " + this.file, e);
      }
   }

   /** 一条待发上报。 */
   public static final class Pending {
      private final long id;
      private final PlayerEventType type;
      private final String playerId;
      private final String playerName;
      private final long eventAt;
      private final String content;

      private Pending(long id, PlayerEventType type, String playerId, String playerName, long eventAt, String content) {
         this.id = id;
         this.type = type;
         this.playerId = playerId;
         this.playerName = playerName;
         this.eventAt = eventAt;
         this.content = content;
      }

      public long getId() {
         return this.id;
      }

      public PlayerEventType getType() {
         return this.type;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public long getEventAt() {
         return this.eventAt;
      }

      /** 还原成上报体（时间戳保持原样，后端按 eventAt 结算）。 */
      public PlayerEventPayload toPayload() {
         return new PlayerEventPayload(this.playerId, this.playerName, this.eventAt, this.content);
      }

      private String toJson() {
         StringBuilder json = new StringBuilder();
         json.append('{');
         json.append("\"id\":").append(this.id);
         json.append(",\"type\":\"").append(JsonObjects.escape(this.type.name())).append('"');
         json.append(",\"playerId\":\"").append(JsonObjects.escape(this.playerId == null ? "" : this.playerId)).append('"');
         json.append(",\"playerName\":\"").append(JsonObjects.escape(this.playerName == null ? "" : this.playerName)).append('"');
         json.append(",\"eventAt\":").append(this.eventAt);
         json.append(",\"content\":");
         if (this.content == null) {
            json.append("null");
         } else {
            json.append('"').append(JsonObjects.escape(this.content)).append('"');
         }

         json.append('}');
         return json.toString();
      }
   }
}
