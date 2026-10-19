package online.yudream.minecraft.bukkit.playtime;

import java.io.IOException;
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
 *         玩家不在线时忽略后端的开区间 —— 那通常是后端还没收到退服事件的残留，
 *         照抄会把离线时间也算成在线。</li>
 * </ul>
 *
 * <p>反向的补齐由 {@code report.PendingReportJournal} 负责（本地事件按原始时间戳补发），
 * 两边最终会收敛到同一个数字。
 */
public final class PlaytimeSync {

   private static final int PAGE_SIZE = 100;
   private static final int MAX_PAGES = 50;

   private final YudreamMinecraftPlugin plugin;

   public PlaytimeSync(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
   }

   /**
    * 拉取后端全量玩家记录并合并进本地台账。
    *
    * @param onlineNow 当前真实在线的玩家（必须由主线程采集后传进来）
    * @return 是否成功（失败时说明原因并保持本地数据不动）
    */
   public boolean pullFromBackend(Set<UUID> onlineNow) {
      PlaytimeLedger ledger = this.plugin.getPlaytimeLedger();
      if (ledger == null || !this.plugin.canReport()) {
         return false;
      }

      int page = 1;
      int rows = 0;
      int updated = 0;
      long now = System.currentTimeMillis();

      while (page <= MAX_PAGES) {
         HttpResult result;
         try {
            result = this.plugin.getApiClient().players(page, PAGE_SIZE);
         } catch (IOException e) {
            this.plugin.getLogger().warning("与后端对齐本地时长失败（第 " + page + " 页）：" + e.getMessage());
            return false;
         }

         if (!result.isSuccess()) {
            this.plugin.getLogger().warning("与后端对齐本地时长失败：HTTP " + result.getStatusCode()
                    + "（列表接口需要 API key 带 plugin:minecraft-server:manage 权限）");
            return false;
         }

         Map<String, Object> body = MiniJson.parseObject(result.getBody());
         Object rawRecords = body == null ? null : body.get("records");
         if (!(rawRecords instanceof List)) {
            this.plugin.getLogger().warning("与后端对齐本地时长失败：响应里没有 records 数组。");
            return false;
         }

         List<?> records = (List<?>)rawRecords;
         for (Object item : records) {
            if (!(item instanceof Map)) {
               continue;
            }

            rows++;
            if (this.merge(ledger, asMap(item), onlineNow, now)) {
               updated++;
            }
         }

         long total = MiniJson.longValue(body, "total", records.size());
         if (records.isEmpty() || (long)page * (long)PAGE_SIZE >= total) {
            break;
         }

         page++;
      }

      ledger.saveIfDirty();
      this.plugin.getLogger().info("本地时长台账已与后端对齐：后端记录 " + rows + " 条，更新本地 " + updated
              + " 条，本地共 " + ledger.size() + " 名玩家。");
      return true;
   }

   private boolean merge(PlaytimeLedger ledger, Map<String, Object> row, Set<UUID> onlineNow, long now) {
      String playerId = MiniJson.string(row, "playerId", "");
      if (playerId.isEmpty()) {
         return false;
      }

      boolean actuallyOnline = false;
      try {
         actuallyOnline = onlineNow.contains(UUID.fromString(playerId));
      } catch (IllegalArgumentException ignored) {
         // 非 UUID 的玩家标识（理论上不会出现）：按不在线处理，只合并已结算累计
      }

      return ledger.mergeFromBackend(
              playerId,
              MiniJson.string(row, "playerName", ""),
              MiniJson.longValue(row, "totalOnlineMillis", 0L),
              MiniJson.longValue(row, "totalAfkMillis", 0L),
              optional(MiniJson.longValue(row, "currentOnlineSince", 0L)),
              optional(MiniJson.longValue(row, "currentAfkSince", 0L)),
              optional(MiniJson.longValue(row, "lastJoinedAt", 0L)),
              optional(MiniJson.longValue(row, "lastQuitAt", 0L)),
              actuallyOnline,
              now
      );
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> asMap(Object value) {
      return (Map<String, Object>)(Map<?, ?>)value;
   }

   private static Long optional(long value) {
      return value <= 0L ? null : Long.valueOf(value);
   }
}
