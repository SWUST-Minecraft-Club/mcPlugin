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

   /**
    * 用后端的记录对齐本地台账（服务器同步后端数据，见 {@link PlaytimeSync}）。
    *
    * <p>合并规则（后端能给出记录时，它就是这个玩家的权威值）：
    * <ul>
    *     <li>已结算累计与当前在线 / 挂机区间都**以后端为准**，同步那一刻两边数字完全一致；
    *         历史上被算重/算多的本地数字也会因此被修正回来；</li>
    *     <li>例外：玩家确实在线、但后端还没有开区间 / 挂机区间（我们的上报还没到）——本地领先，
    *         保留本地这一段，等补发完成后端追上来；</li>
    *     <li>玩家此刻不在线时同样采用后端的（不含开区间的）累计，避免把离线时间算成在线；</li>
    *     <li>名字以非空为准；最近上线取更早、最近下线取更晚。</li>
    * </ul>
    *
    * @return 本地记录是否发生变化
    */
   public synchronized boolean mergeFromBackend(String playerId, String playerName,
                                                long backendOnlineMillis, long backendAfkMillis,
                                                Long backendOnlineSince, Long backendAfkSince,
                                                Long backendLastJoinedAt, Long backendLastQuitAt,
                                                boolean actuallyOnline, long now) {
      if (playerId == null || playerId.isEmpty()) {
         return false;
      }

      Entry entry = this.entries.get(playerId);
      boolean created = false;
      if (entry == null) {
         entry = new Entry(playerId);
         this.entries.put(playerId, entry);
         created = true;
      }

      boolean changed = created;
      long localSettledOnline = entry.onlineMillis;
      long localSettledAfk = entry.afkMillis;
      boolean backendSaysOnline = backendOnlineSince != null;
      boolean backendSaysAfk = backendAfkSince != null;

      // 已结算累计与当前区间都**以后端为准**（"服务器同步后端数据"）：
      // 后端可用时它就是权威值，两边在同步那一刻数字完全一致，历史上被算重/算多的本地数字也会被修正回来。
      long nextOnlineMillis = backendOnlineMillis;
      long nextAfkMillis = backendAfkMillis;
      Long nextOnlineSince = backendOnlineSince;
      Long nextAfkSince = backendAfkSince;

      // 例外一：玩家确实在线、但后端还没有开区间（我们的进服上报还没到）——本地领先，保留本地会话。
      if (actuallyOnline && !backendSaysOnline) {
         nextOnlineMillis = Math.max(backendOnlineMillis, localSettledOnline);
         nextOnlineSince = entry.currentOnlineSince;
      }

      // 例外二：本地判定在挂机、后端还没收到挂机上报时，同样保留本地的挂机区间。
      if (actuallyOnline && !backendSaysAfk && entry.currentAfkSince != null) {
         nextAfkMillis = Math.max(backendAfkMillis, localSettledAfk);
         nextAfkSince = entry.currentAfkSince;
      }

      if (nextOnlineMillis != entry.onlineMillis) {
         entry.onlineMillis = nextOnlineMillis;
         changed = true;
      }

      if (!same(nextOnlineSince, entry.currentOnlineSince)) {
         entry.currentOnlineSince = nextOnlineSince;
         changed = true;
      }

      if (nextAfkMillis != entry.afkMillis) {
         entry.afkMillis = nextAfkMillis;
         changed = true;
      }

      if (!same(nextAfkSince, entry.currentAfkSince)) {
         entry.currentAfkSince = nextAfkSince;
         changed = true;
      }

      // 与后端活动桶的不变量一致：不在线时挂机区间必为空
      if (entry.currentOnlineSince == null && entry.currentAfkSince != null) {
         entry.currentAfkSince = null;
         changed = true;
      }

      String merged = name(playerName, entry.playerName);
      if (!merged.equals(entry.playerName)) {
         entry.playerName = merged;
         changed = true;
      }

      if (backendLastJoinedAt != null
              && (entry.lastJoinedAt == null || backendLastJoinedAt.longValue() < entry.lastJoinedAt.longValue())) {
         entry.lastJoinedAt = backendLastJoinedAt;
         changed = true;
      }

      if (backendLastQuitAt != null
              && (entry.lastQuitAt == null || backendLastQuitAt.longValue() > entry.lastQuitAt.longValue())) {
         entry.lastQuitAt = backendLastQuitAt;
         changed = true;
      }

      if (changed) {
         entry.updatedAt = now;
         this.dirty = true;
      }

      return changed;
   }

   private static boolean same(Long left, Long right) {
      if (left == null) {
         return right == null;
      }

      return right != null && left.longValue() == right.longValue();
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
