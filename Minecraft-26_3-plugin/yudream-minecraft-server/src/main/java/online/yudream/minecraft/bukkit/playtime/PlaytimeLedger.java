package online.yudream.minecraft.bukkit.playtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 本地在线时长台账。字段与结算规则逐条对齐 YuDream Admin 后端的 <b>子服活动桶</b>
 * （单桶版：Bukkit 端上报不带子服维度，后端会归入默认桶，所以本地也只需要一个桶）：
 *
 * <ul>
 *     <li>在线时长 = 已结算区间之和 + 当前未结算区间；<b>AFK 时长单独累加，不从在线时长里扣</b>。</li>
 *     <li>重复上线不重置起点（避免把时长算丢）；重复下线不会重复结算。</li>
 *     <li>AFK 开始前若不在线，先按上线处理；结算时按区间时长累加，区间为负记 0。</li>
 *     <li>服务端重启不做特殊处理：没收到退服事件时区间保持打开，与后端看到的完全一致。</li>
 * </ul>
 *
 * <p>数据落在插件数据目录的 {@code playtime.yml}：
 *
 * <pre>
 * players:
 *   &lt;玩家 UUID&gt;:
 *     name: 玩家名
 *     online-millis: 已结算在线毫秒
 *     afk-millis: 已结算挂机毫秒
 *     current-online-since: 当前在线区间起点（0 = 不在线）
 *     current-afk-since: 当前挂机区间起点（0 = 未挂机）
 *     last-joined-at: 最近一次上线时间
 *     last-quit-at: 最近一次下线时间
 *     updated-at: 本记录最近更新时间
 * </pre>
 *
 * <p>本文件同时是伴侣插件（YudreamPlaytime）的数据来源，格式变更需同步那边。
 */
public final class PlaytimeLedger {

   private static final String FILE_NAME = "playtime.yml";

   private final JavaPlugin plugin;
   private final File file;
   private final Map<String, Entry> entries = new LinkedHashMap<>();
   private boolean dirty;

   public PlaytimeLedger(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), FILE_NAME);
      this.load();
   }

   public File getFile() {
      return this.file;
   }

   public synchronized int size() {
      return this.entries.size();
   }

   /** 上线：已经在线的重复加入不重置起点。 */
   public synchronized void join(UUID playerId, String playerName, long eventAt) {
      Entry entry = this.entry(playerId, playerName, eventAt);
      entry.playerName = name(playerName, entry.playerName);
      if (entry.currentOnlineSince == null) {
         entry.currentOnlineSince = Long.valueOf(eventAt);
         entry.lastJoinedAt = Long.valueOf(eventAt);
      }

      entry.updatedAt = System.currentTimeMillis();
      this.dirty = true;
   }

   /** 下线：结算当前在线与挂机区间；不在线时只更新名字。 */
   public synchronized void quit(UUID playerId, String playerName, long eventAt) {
      Entry entry = this.entry(playerId, playerName, eventAt);
      entry.playerName = name(playerName, entry.playerName);
      entry.onlineMillis += duration(entry.currentOnlineSince, eventAt);
      entry.afkMillis += duration(entry.currentAfkSince, eventAt);
      entry.currentOnlineSince = null;
      entry.currentAfkSince = null;
      entry.lastQuitAt = Long.valueOf(eventAt);
      entry.updatedAt = System.currentTimeMillis();
      this.dirty = true;
   }

   /** 开始挂机：不在线时先按上线处理；已挂机不重复开始。 */
   public synchronized void startAfk(UUID playerId, String playerName, long eventAt) {
      Entry entry = this.entry(playerId, playerName, eventAt);
      entry.playerName = name(playerName, entry.playerName);
      if (entry.currentOnlineSince == null) {
         entry.currentOnlineSince = Long.valueOf(eventAt);
         entry.lastJoinedAt = Long.valueOf(eventAt);
      }

      if (entry.currentAfkSince == null) {
         entry.currentAfkSince = Long.valueOf(eventAt);
      }

      entry.updatedAt = System.currentTimeMillis();
      this.dirty = true;
   }

   /** 结束挂机：结算挂机区间；没在挂机时什么也不做。 */
   public synchronized void endAfk(UUID playerId, String playerName, long eventAt) {
      Entry entry = this.entry(playerId, playerName, eventAt);
      entry.playerName = name(playerName, entry.playerName);
      if (entry.currentAfkSince != null) {
         entry.afkMillis += duration(entry.currentAfkSince, eventAt);
         entry.currentAfkSince = null;
         entry.updatedAt = System.currentTimeMillis();
         this.dirty = true;
      }
   }

   /** 含当前未结算区间的在线时长（与后端 {@code totalOnlineMillisAt(now)} 同一算法）。 */
   public synchronized long onlineMillisAt(UUID playerId, long now) {
      Entry entry = this.entries.get(key(playerId));
      return entry == null ? 0L : entry.onlineMillis + duration(entry.currentOnlineSince, now);
   }

   /** 含当前未结算区间的挂机时长。 */
   public synchronized long afkMillisAt(UUID playerId, long now) {
      Entry entry = this.entries.get(key(playerId));
      return entry == null ? 0L : entry.afkMillis + duration(entry.currentAfkSince, now);
   }

   public synchronized String nameOf(UUID playerId) {
      Entry entry = this.entries.get(key(playerId));
      return entry == null ? null : entry.playerName;
   }

   public synchronized boolean isOnline(UUID playerId) {
      Entry entry = this.entries.get(key(playerId));
      return entry != null && entry.currentOnlineSince != null;
   }

   public synchronized boolean isAfk(UUID playerId) {
      Entry entry = this.entries.get(key(playerId));
      return entry != null && entry.currentAfkSince != null;
   }

   /** 按玩家名找一个记录（用于离线查询），找不到返回 {@code null}。 */
   public synchronized UUID findByName(String playerName) {
      if (playerName == null || playerName.isEmpty()) {
         return null;
      }

      for (Entry entry : this.entries.values()) {
         if (playerName.equalsIgnoreCase(entry.playerName)) {
            try {
               return UUID.fromString(entry.playerId);
            } catch (IllegalArgumentException ignored) {
               return null;
            }
         }
      }

      return null;
   }

   /** 有变化才落盘；由插件按 {@code local-playtime.save-interval-seconds} 周期调用。 */
   public synchronized void saveIfDirty() {
      if (!this.dirty) {
         return;
      }

      if (this.saveNow()) {
         this.dirty = false;
      }
   }

   /** 立即落盘（插件停用、命令查询前调用）。 */
   public synchronized boolean saveNow() {
      YamlConfiguration yaml = new YamlConfiguration();
      ConfigurationSection playersSection = yaml.createSection("players");

      for (Entry entry : this.entries.values()) {
         ConfigurationSection section = playersSection.createSection(entry.playerId);
         section.set("name", entry.playerName);
         section.set("online-millis", Long.valueOf(entry.onlineMillis));
         section.set("afk-millis", Long.valueOf(entry.afkMillis));
         section.set("current-online-since", Long.valueOf(entry.currentOnlineSince == null ? 0L : entry.currentOnlineSince.longValue()));
         section.set("current-afk-since", Long.valueOf(entry.currentAfkSince == null ? 0L : entry.currentAfkSince.longValue()));
         section.set("last-joined-at", Long.valueOf(entry.lastJoinedAt == null ? 0L : entry.lastJoinedAt.longValue()));
         section.set("last-quit-at", Long.valueOf(entry.lastQuitAt == null ? 0L : entry.lastQuitAt.longValue()));
         section.set("updated-at", Long.valueOf(entry.updatedAt));
      }

      Path target = this.file.toPath();
      Path parent = target.getParent();

      try {
         if (parent != null) {
            Files.createDirectories(parent);
         }

         Path temp = parent == null ? this.file.toPath().resolveSibling(FILE_NAME + ".tmp")
                 : parent.resolve(FILE_NAME + ".tmp");
         Files.write(temp, yaml.saveToString().getBytes("UTF-8"));
         try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomicUnsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
         }

         return true;
      } catch (IOException e) {
         this.plugin.getLogger().log(Level.WARNING, "Could not write the local playtime ledger to " + this.file, e);
         return false;
      }
   }

   private void load() {
      if (!this.file.isFile()) {
         return;
      }

      try {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection playersSection = yaml.getConfigurationSection("players");
         if (playersSection == null) {
            return;
         }

         for (String playerId : playersSection.getKeys(false)) {
            ConfigurationSection section = playersSection.getConfigurationSection(playerId);
            if (section == null) {
               continue;
            }

            Entry entry = new Entry(playerId);
            entry.playerName = section.getString("name", "");
            entry.onlineMillis = Math.max(section.getLong("online-millis", 0L), 0L);
            entry.afkMillis = Math.max(section.getLong("afk-millis", 0L), 0L);
            entry.currentOnlineSince = optional(section.getLong("current-online-since", 0L));
            entry.currentAfkSince = entry.currentOnlineSince == null ? null : optional(section.getLong("current-afk-since", 0L));
            entry.lastJoinedAt = optional(section.getLong("last-joined-at", 0L));
            entry.lastQuitAt = optional(section.getLong("last-quit-at", 0L));
            entry.updatedAt = section.getLong("updated-at", 0L);
            this.entries.put(playerId, entry);
         }

         this.plugin.getLogger().info("本地在线时长台账已加载：" + this.entries.size() + " 名玩家（" + this.file.getName() + "）。");
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.WARNING, "Could not read the local playtime ledger from " + this.file, e);
      }
   }

   private Entry entry(UUID playerId, String playerName, long eventAt) {
      String key = key(playerId);
      Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new Entry(key);
         entry.playerName = name(playerName, "");
         entry.updatedAt = eventAt;
         this.entries.put(key, entry);
      }

      return entry;
   }

   private static Long optional(long value) {
      return value <= 0L ? null : Long.valueOf(value);
   }

   private static String key(UUID playerId) {
      return playerId.toString();
   }

   private static String name(String candidate, String fallback) {
      if (candidate != null && !candidate.trim().isEmpty()) {
         return candidate.trim();
      }

      return fallback == null ? "" : fallback;
   }

   private static long duration(Long startAt, long endAt) {
      if (startAt == null || endAt <= startAt.longValue()) {
         return 0L;
      }

      return endAt - startAt.longValue();
   }

   /** 一个玩家的本地记录（与后端子服活动桶字段一一对应）。 */
   private static final class Entry {
      private final String playerId;
      private String playerName = "";
      private long onlineMillis;
      private long afkMillis;
      private Long currentOnlineSince;
      private Long currentAfkSince;
      private Long lastJoinedAt;
      private Long lastQuitAt;
      private long updatedAt;

      private Entry(String playerId) {
         this.playerId = playerId;
      }
   }
}
