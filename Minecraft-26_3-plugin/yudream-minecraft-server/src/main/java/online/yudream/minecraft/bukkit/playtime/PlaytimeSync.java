package online.yudream.minecraft.bukkit.playtime;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.api.HttpResult;
import online.yudream.minecraft.bukkit.bridge.MiniJson;

/**
 * 把本地台账与 YuDream Admin 后端的记录对齐（"服务器同步后端数据"）。
 *
 * <p>为什么需要它：本地台账只从本插件开始记账的那一刻算起，而后端可能有更早的历史
 * （换新版插件之前的上报、代理后端上报的数据），所以同一个人两边的数字会差一个历史量。
 * 这里定期把后端的每玩家累计（已结算在线 / 挂机毫秒 + 当前区间起点）拉下来合并：
 *
 * <ul>
 *     <li>已结算累计取**两边较大值**：本地可能有还没补发出去的事件，后端也可能有更早的历史。</li>
 *     <li>当前区间只有在"玩家此刻确实在线"时才采用后端的起点，并取**更早**的那个（会话真正开始的时间）；
 *         玩家不在线时忽略后端的开区间 —— 那通常是后端还没收到退服的残留，照抄会把离线时间也算成在线。</li>
 * </ul>
 *
 * <p>反向的补齐由 {@code report.PendingReportJournal} 负责（本地事件按原始时间戳补发），
 * 两边最终会收敛到同一个数字。
 *
 * <p>接口细节（容易踩的两处，都按"能读到就认"处理）：
 * <ul>
 *     <li>响应可能被平台包一层 {@code {"code":..,"message":..,"data":{...}}}（{@code PluginHttpResponse.ok} 默认 wrapped），
 *         所以取 {@code data}；没有包就直接用顶层。</li>
 *     <li>平台把长整型序列化成**字符串**（信封里的 timestamp 就是字符串），
 *         所以毫秒字段要同时接受数字与字符串两种写法。</li>
 * </ul>
 */
public final class PlaytimeSync {

   private static final int PAGE_SIZE = 100;
   private static final int MAX_PAGES = 50;
   private static final long FAILURE_LOG_INTERVAL_MS = 30L * 60L * 1000L;

   private final YudreamMinecraftPlugin plugin;
   private volatile long lastAttemptAt;
   private volatile String lastResult = "尚未尝试";
   /** 上次成功的路径：管理端优先，失败过就记住另一条并写盘，重启后直接走它，避免反复撞 5xx。 */
   private volatile boolean preferAdminPath;
   private long lastFailureLoggedAt;

   public PlaytimeSync(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
      this.preferAdminPath = this.loadPreferredPath();
   }

   /** 最近一次对齐的结果（给 /yudreammc status 与 syncplaytime 用，省得翻日志）。 */
   public String describeLastResult() {
      return this.lastResult + "（接口：" + (this.preferAdminPath ? "管理端" : "用户端") + "）";
   }

   public long getLastAttemptAt() {
      return this.lastAttemptAt;
   }

   /**
    * 拉取后端全量玩家记录并合并进本地台账。
    *
    * @param onlineNow 当前真实在线的玩家（必须由主线程采集后传进来）
    * @return 是否成功（失败时说明原因并保持本地数据不动）
    */
   public boolean pullFromBackend(Set<UUID> onlineNow) {
      PlaytimeLedger ledger = this.plugin.getPlaytimeLedger();
      this.lastAttemptAt = System.currentTimeMillis();
      if (ledger == null) {
         this.lastResult = "失败：本地台账不可用";
         return false;
      }

      if (!this.plugin.canReport()) {
         this.lastResult = "跳过：当前模式不直连后端（下游模式 / 未配置 / 总开关关闭）";
         return false;
      }

      int page = 1;
      int rows = 0;
      int updated = 0;
      long now = System.currentTimeMillis();
      String sample = null;

      while (page <= MAX_PAGES) {
         HttpResult result;
         try {
            result = this.fetchPlayersPage(page);
         } catch (IOException e) {
            this.lastResult = "失败：第 " + page + " 页请求异常（" + e.getMessage() + "）";
            this.plugin.getLogger().warning("与后端对齐本地时长失败（第 " + page + " 页）：" + e.getMessage());
            return false;
         }

         if (!result.isSuccess()) {
            this.lastResult = "失败：HTTP " + result.getStatusCode()
                    + "（管理端列表接口需要 API key 带 plugin:minecraft-server:manage 权限）"
                    + "，响应=" + shorten(result.getBody());
            return false;
         }

         Map<String, Object> payload = payloadOf(MiniJson.parseObject(result.getBody()));
         List<?> records = listOf(payload, "records", "list", "items");
         if (records == null) {
            this.lastResult = "失败：响应里找不到玩家数组，响应=" + shorten(result.getBody());
            this.plugin.getLogger().warning("与后端对齐本地时长失败：响应里找不到玩家数组，body=" + shorten(result.getBody()));
            return false;
         }

         for (Object item : records) {
            if (!(item instanceof Map)) {
               continue;
            }

            rows++;
            Map<String, Object> row = asMap(item);
            if (sample == null) {
               sample = shorten(row.toString());
            }

            if (this.merge(ledger, row, onlineNow, now)) {
               updated++;
            }
         }

         long total = longOf(payload, records.size(), "total", "count");
         if (records.isEmpty() || (long)page * (long)PAGE_SIZE >= total) {
            break;
         }

         page++;
      }

      ledger.saveIfDirty();
      this.lastResult = "成功：后端记录 " + rows + " 条，更新本地 " + updated + " 条，本地共 " + ledger.size() + " 名玩家"
              + (rows > 0 && updated == 0 ? "；没有任何字段被采纳，记录样例=" + sample : "");
      if (updated > 0) {
         this.plugin.getLogger().info("本地时长台账已与后端对齐：" + this.lastResult);
      } else {
         // 没有变化是常态（两边已经一致），不刷 INFO；只在 FINE 里留痕
         this.plugin.getLogger().fine("本地时长台账已与后端一致（无变化）：" + this.lastResult);
      }

      return true;
   }

   /**
    * 取一页玩家记录：优先上次成功的路径，失败（含 5xx）就换另一条路径再试一次。
    *
    * <p>管理端路径在部分部署上会返回 500，用户端路径在旧后端上可能不存在，所以两边都要能兜。
    * 探测结果会写进 {@code sync-endpoint.txt}：重启后直接走可用的那条，不再每次白撞一次 5xx；
    * 只有**两条都失败**时才打告警，且 30 分钟内最多一条（避免控制台被刷屏）。
    */
   private HttpResult fetchPlayersPage(int page) throws IOException {
      HttpResult result = this.plugin.getApiClient().playersAt(this.preferAdminPath, page, PAGE_SIZE);
      if (result.isSuccess()) {
         return result;
      }

      HttpResult alternative = this.plugin.getApiClient().playersAt(!this.preferAdminPath, page, PAGE_SIZE);
      if (alternative.isSuccess()) {
         this.preferAdminPath = !this.preferAdminPath;
         this.savePreferredPath();
         this.plugin.getLogger().info("玩家列表接口切换到" + (this.preferAdminPath ? "管理端" : "用户端")
                 + "路径（另一条返回 HTTP " + result.getStatusCode() + "），之后都用这条。");
         return alternative;
      }

      this.warnThrottled("与后端对齐本地时长失败：管理端 HTTP " + result.getStatusCode()
              + "、用户端 HTTP " + alternative.getStatusCode()
              + "（管理端列表接口需要 API key 带 plugin:minecraft-server:manage 权限）");
      return result;
   }

   /** 两条路径都失败时的告警节流（30 分钟一条）。 */
   private void warnThrottled(String message) {
      long now = System.currentTimeMillis();
      if (now - this.lastFailureLoggedAt < FAILURE_LOG_INTERVAL_MS) {
         return;
      }

      this.lastFailureLoggedAt = now;
      this.plugin.getLogger().warning(message);
   }

   private File endpointStateFile() {
      return new File(this.plugin.getDataFolder(), "sync-endpoint.txt");
   }

   /** 读上次成功的路径（{@code admin} / {@code legacy}），没有记录时默认先试管理端。 */
   private boolean loadPreferredPath() {
      File file = this.endpointStateFile();
      if (!file.isFile()) {
         return true;
      }

      try {
         String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
         return !"legacy".equalsIgnoreCase(text);
      } catch (IOException e) {
         return true;
      }
   }

   private void savePreferredPath() {
      try {
         Files.write(this.endpointStateFile().toPath(),
                 (this.preferAdminPath ? "admin" : "legacy").getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         this.plugin.getLogger().fine("无法保存同步接口路径：" + e.getMessage());
      }
   }

   private boolean merge(PlaytimeLedger ledger, Map<String, Object> row, Set<UUID> onlineNow, long now) {
      String playerId = stringOf(row, "", "playerId", "id", "uuid");
      if (playerId.isEmpty()) {
         return false;
      }

      boolean actuallyOnline = false;
      try {
         actuallyOnline = onlineNow.contains(UUID.fromString(playerId));
      } catch (IllegalArgumentException ignored) {
         // 非 UUID 的玩家标识（理论上不会出现）：按不在线处理，只合并已结算累计
      }

      // 后端返回的累计是 totalOnlineMillisAt(now) / totalAfkMillisAt(now)：**已经含当前未结算区间**
      // （见后端 assembler.toDTO）。直接当"已结算值"存进本地，会和本地自己那段开区间重复计时，
      // 所以这里先按各自的起点把开区间减掉，还原成真正的已结算值。
      Long backendOnlineSince = optionalLongOf(row, "currentOnlineSince", "onlineSince");
      Long backendAfkSince = optionalLongOf(row, "currentAfkSince", "afkSince");

      long backendOnlineMillis = longOf(row, 0L, "totalOnlineMillis", "onlineMillis", "totalOnline", "onlineMillisTotal");
      long backendAfkMillis = longOf(row, 0L, "totalAfkMillis", "afkMillis", "totalAfk", "afkMillisTotal");
      backendOnlineMillis = settled(backendOnlineMillis, backendOnlineSince, now);
      backendAfkMillis = settled(backendAfkMillis, backendAfkSince, now);

      // 顶层没有累计时退一步：按子服明细求和（每个子服的时长同样是"含未结算区间"的口径）
      if (backendOnlineMillis <= 0L || backendAfkMillis <= 0L) {
         List<?> subServers = listOf(row, "subServers", "subservers");
         if (subServers != null) {
            long online = 0L;
            long afk = 0L;
            for (Object sub : subServers) {
               if (sub instanceof Map) {
                  Map<String, Object> bucket = asMap(sub);
                  online += settled(longOf(bucket, 0L, "onlineMillis", "totalOnlineMillis"),
                          optionalLongOf(bucket, "currentOnlineSince", "onlineSince"), now);
                  afk += settled(longOf(bucket, 0L, "afkMillis", "totalAfkMillis"),
                          optionalLongOf(bucket, "currentAfkSince", "afkSince"), now);
               }
            }

            backendOnlineMillis = Math.max(backendOnlineMillis, online);
            backendAfkMillis = Math.max(backendAfkMillis, afk);
         }
      }

      return ledger.mergeFromBackend(
              playerId,
              stringOf(row, "", "playerName", "name"),
              backendOnlineMillis,
              backendAfkMillis,
              optionalLongOf(row, "currentOnlineSince", "onlineSince"),
              optionalLongOf(row, "currentAfkSince", "afkSince"),
              optionalLongOf(row, "lastJoinedAt", "joinedAt"),
              optionalLongOf(row, "lastQuitAt", "quitAt"),
              actuallyOnline,
              now
      );
   }

   /**
    * 取数据载荷：平台默认把响应包成 {@code {"code":..,"message":..,"data":..}}（{@code PluginHttpResponse.ok} 的
    * {@code wrapped=true}），真正的 records / total 在 {@code data} 里；没包一层时直接用顶层。
    */
   private static Map<String, Object> payloadOf(Map<String, Object> body) {
      if (body == null) {
         return null;
      }

      Object data = body.get("data");
      return data instanceof Map ? asMap(data) : body;
   }

   /** 按键取列表，支持多个别名与大小写不敏感。 */
   private static List<?> listOf(Map<String, Object> map, String... keys) {
      Object value = valueOf(map, keys);
      return value instanceof List ? (List<?>) value : null;
   }

   private static long longOf(Map<String, Object> map, long fallback, String... keys) {
      Object value = valueOf(map, keys);
      if (value instanceof Number) {
         return ((Number)value).longValue();
      }

      if (value instanceof String) {
         try {
            return Long.parseLong(((String)value).trim());
         } catch (NumberFormatException ignored) {
            return fallback;
         }
      }

      return fallback;
   }

   private static Long optionalLongOf(Map<String, Object> map, String... keys) {
      long value = longOf(map, 0L, keys);
      return value <= 0L ? null : Long.valueOf(value);
   }

   private static String stringOf(Map<String, Object> map, String fallback, String... keys) {
      Object value = valueOf(map, keys);
      if (value == null) {
         return fallback;
      }

      return value instanceof String ? (String)value : String.valueOf(value);
   }

   /** 把"含未结算区间"的累计还原成已结算值（后端返回的是前者）。 */
   private static long settled(long totalWithOpenInterval, Long openSince, long now) {
      long open = openSince == null || now <= openSince.longValue() ? 0L : now - openSince.longValue();
      return Math.max(totalWithOpenInterval - open, 0L);
   }

   /**
    * 后端侧看到的某个玩家的数字（口径与后端界面完全一致：含当前未结算区间）。
    *
    * <p>给 {@code /yudreammc playtime} 做"本地 vs 后端"对照用；找不到返回 {@code null}。
    */
   public BackendNumbers fetchPlayerFromBackend(String playerId) {
      if (playerId == null || playerId.isEmpty() || !this.plugin.canReport()) {
         return null;
      }

      long now = System.currentTimeMillis();
      for (int page = 1; page <= MAX_PAGES; page++) {
         HttpResult result;
         try {
            result = this.fetchPlayersPage(page);
         } catch (IOException e) {
            this.plugin.getLogger().warning("查询后端玩家记录失败：" + e.getMessage());
            return null;
         }

         if (!result.isSuccess()) {
            this.plugin.getLogger().warning("查询后端玩家记录失败：HTTP " + result.getStatusCode());
            return null;
         }

         List<?> records = listOf(payloadOf(MiniJson.parseObject(result.getBody())), "records", "list", "items");
         if (records == null) {
            return null;
         }

         for (Object item : records) {
            if (!(item instanceof Map)) {
               continue;
            }

            Map<String, Object> row = asMap(item);
            if (!playerId.equalsIgnoreCase(stringOf(row, "", "playerId", "id", "uuid"))) {
               continue;
            }

            return new BackendNumbers(
                    stringOf(row, "", "playerName", "name"),
                    longOf(row, 0L, "totalOnlineMillis", "onlineMillis", "totalOnline"),
                    longOf(row, 0L, "totalAfkMillis", "afkMillis", "totalAfk"),
                    boolOf(row, "online")
            );
         }

         if (records.isEmpty()) {
            return null;
         }
      }

      return null;
   }

   private static boolean boolOf(Map<String, Object> map, String... keys) {
      Object value = valueOf(map, keys);
      if (value instanceof Boolean) {
         return (Boolean)value;
      }

      return value instanceof String && Boolean.parseBoolean((String)value);
   }

   /** 后端侧的玩家数字（含未结算区间，与后端界面口径一致）。 */
   public static final class BackendNumbers {
      private final String playerName;
      private final long onlineMillis;
      private final long afkMillis;
      private final boolean online;

      private BackendNumbers(String playerName, long onlineMillis, long afkMillis, boolean online) {
         this.playerName = playerName;
         this.onlineMillis = onlineMillis;
         this.afkMillis = afkMillis;
         this.online = online;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public long getOnlineMillis() {
         return this.onlineMillis;
      }

      public long getAfkMillis() {
         return this.afkMillis;
      }

      public boolean isOnline() {
         return this.online;
      }
   }

   /** 键取值：先精确匹配，再忽略大小写匹配（部署版本字段大小写不一致时不至于全空）。 */
   private static Object valueOf(Map<String, Object> map, String... keys) {
      if (map == null) {
         return null;
      }

      for (String key : keys) {
         if (map.containsKey(key)) {
            return map.get(key);
         }
      }

      for (Map.Entry<String, Object> entry : map.entrySet()) {
         String existing = entry.getKey();
         if (existing == null) {
            continue;
         }

         for (String key : keys) {
            if (existing.equalsIgnoreCase(key)) {
               return entry.getValue();
            }
         }
      }

      return null;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> asMap(Object value) {
      return (Map<String, Object>)(Map<?, ?>)value;
   }

   private static String shorten(String text) {
      if (text == null) {
         return "";
      }

      String flattened = text.replace('\n', ' ').trim();
      return flattened.length() <= 400 ? flattened : flattened.substring(0, 400) + "...";
   }
}
