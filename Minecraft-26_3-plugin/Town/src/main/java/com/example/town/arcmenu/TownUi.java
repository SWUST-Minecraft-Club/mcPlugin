package com.example.town.arcmenu;

import com.example.town.TownConfig;
import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.model.Role;
import com.example.town.model.TownSnapshotView;
import com.example.town.model.TownState;
import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuPointerMode;
import com.fentai.arcmenu.api.ArcMenuReloadReport;
import com.fentai.arcmenu.api.ArcMenuStateScope;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小镇界面粘合层：打开菜单前把该玩家相关的一切动态值写入 ArcMenu 玩家 META
 * （菜单 YAML 里用 {meta:…} 占位），变更后重写并触发 refresh。
 *
 * <p>规则（与 arcmenu-designing 技能一致）：视觉全部交给 ArcMenu 渲染器；
 * 本类只负责数据与打开/刷新时机，绝不自行放置实体。
 */
public final class TownUi {

    /** 成员列表每页行数（与 town-members.yml 的行数一致）。 */
    public static final int MEMBER_ROWS = 10;
    /** 圈地地图边长（9×9）。 */
    public static final int CLAIM_GRID = 9;
    private static final String SLOT_EMPTY = "—";

    private final TownPlugin plugin;
    private final ArcMenuApi api;
    /** 每个玩家当前所在菜单（refresh 时按屏重写）。 */
    private final Map<UUID, String> current = new ConcurrentHashMap<>();
    /** 成员列表当前页 / 圈地地图中心（refresh 时沿用）。 */
    private final Map<UUID, Integer> memberPage = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkKey> claimCenter = new ConcurrentHashMap<>();

    private TownUi(final TownPlugin plugin, final ArcMenuApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    /** ArcMenu 未装 / API 不可用时返回 null。 */
    public static TownUi create(final TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("ArcMenu") == null) {
            return null;
        }
        try {
            final ArcMenuApi api = plugin.getServer().getServicesManager().load(ArcMenuApi.class);
            if (api == null) {
                plugin.getLogger().warning("ArcMenu 已安装但 API 未注册，图形界面降级为指令。");
                return null;
            }
            plugin.getLogger().info("已接入 ArcMenu（apiVersion=" + api.apiVersion() + "）。");
            return new TownUi(plugin, api);
        } catch (final LinkageError e) {
            plugin.getLogger().warning("ArcMenu API 类不可用，图形界面禁用：" + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 生成菜单文件并让 ArcMenu 重新加载（启动 / /town admin reload）。 */
    public void installMenus() {
        final Path dir = plugin.getDataFolder().toPath().getParent()
                .resolve("ArcMenu").resolve("menus");
        try {
            final List<TownMenus.Written> written = TownMenus.writeAll(plugin, dir);
            plugin.getLogger().info("已生成 " + written.size() + " 个小镇菜单文件到 " + dir);
        } catch (final IOException e) {
            plugin.getLogger().warning("小镇菜单写入失败：" + e.getMessage());
            return;
        }
        final ArcMenuReloadReport report = api.menus().reload();
        if (report.isApplied()) {
            plugin.getLogger().info("ArcMenu 菜单已重载：共 " + report.getMenuCount() + " 个，无错误。");
        } else {
            plugin.getLogger().warning("ArcMenu 菜单重载失败（" + report.getErrors().size() + " 个错误）：");
            report.getErrors().stream().limit(10).forEach(line -> plugin.getLogger().warning("  · " + line));
        }
    }

    public void shutdown() {
        // META 是每玩家内存态，无需清理
    }

    // ------------------------------------------------------------------ 打开

    public void openHome(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final String id;
        if (view == null) {
            final TownSnapshotView invite = plugin.service().inviteOf(player.getUniqueId());
            writeCommon(player, null);
            if (invite != null) {
                set(player, "invited_from", invite.name());
            } else {
                set(player, "invited_from", null);
            }
            id = TownMenus.HOME_NONE;
        } else if (!view.active()) {
            writeCommon(player, view);
            id = TownMenus.HOME_PENDING;
        } else {
            writeCommon(player, view);
            id = TownMenus.HOME_ACTIVE;
        }
        markAndOpen(player, id);
    }

    public void openMembers(final Player player, final int page) {
        this.memberPage.put(player.getUniqueId(), Math.max(1, page));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeMembers(player, Math.max(1, page));
        markAndOpen(player, TownMenus.MEMBERS);
    }

    public void openClaim(final Player player) {
        this.claimCenter.put(player.getUniqueId(), ChunkKey.of(player.getLocation()));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeClaim(player);
        markAndOpen(player, TownMenus.CLAIM);
    }

    public void openBuffs(final Player player) {
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeBuffs(player);
        markAndOpen(player, TownMenus.BUFFS);
    }

    /** 状态变化后调用：重写全部 meta 并刷新当前菜单（无会话时静默）。 */
    public void refresh(final Player player) {
        final String screen = this.current.get(player.getUniqueId());
        if (screen == null) {
            return;
        }
        writeAll(player);
        this.api.refresh(player);
    }

    public void forget(final UUID playerId) {
        this.current.remove(playerId);
        this.memberPage.remove(playerId);
        this.claimCenter.remove(playerId);
    }

    private void markAndOpen(final Player player, final String menuId) {
        this.current.put(player.getUniqueId(), menuId);
        // 本套界面按鼠标光标设计（格线命中、圈地左键/右键），兜底切换，防服务器策略被改回触控
        this.api.setPointerMode(player, ArcMenuPointerMode.MOUSE);
        this.api.open(player, menuId);
    }

    // ------------------------------------------------------------------ meta 写入

    private void set(final Player player, final String key, final String value) {
        if (value == null) {
            this.api.state().remove(player, ArcMenuStateScope.META, key);
        } else {
            this.api.state().set(player, ArcMenuStateScope.META, key, value);
        }
    }

    /** 一次写全（打开与刷新共用，省得追踪哪屏缺哪个键）。 */
    private void writeAll(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        writeCommon(player, view);
        writeMembers(player, this.memberPage.getOrDefault(player.getUniqueId(), 1));
        if (this.claimCenter.containsKey(player.getUniqueId())) {
            writeClaim(player);
        }
        writeBuffs(player);
    }

    private void writeCommon(final Player player, final TownSnapshotView view) {
        final TownConfig cfg = plugin.config();
        set(player, "balance", balanceText(player));
        set(player, "name_len", cfg.nameMinLength() + "~" + cfg.nameMaxLength() + " 字符");
        set(player, "min_members", String.valueOf(cfg.minMembers()));
        set(player, "found_cost", costText(cfg.foundingCost()));
        set(player, "claim_price", costText(cfg.pricePerChunk()));
        set(player, "claim_max", cfg.maxChunksPerTown() > 0 ? String.valueOf(cfg.maxChunksPerTown()) : "∞");
        final UUID self = player.getUniqueId();
        final TownSnapshotView invite = plugin.service().inviteOf(self);
        set(player, "invited_from", invite == null ? null : invite.name());
        if (view == null) {
            set(player, "town_name", null);
            set(player, "is_mayor", null);
            set(player, "has_spawn", null);
            set(player, "can_found", null);
            return;
        }
        set(player, "town_name", view.name());
        set(player, "member_count", String.valueOf(view.memberCount()));
        set(player, "claim_count", String.valueOf(view.claimCount()));
        set(player, "buff_count", String.valueOf(view.buffs().size()));
        set(player, "mayor_name", nameOf(view.mayor()));
        set(player, "is_mayor", view.mayor().equals(self) ? "1" : "0");
        set(player, "has_spawn", view.spawnSet() ? "1" : "0");
        set(player, "created_date", view.createdDate());
        set(player, "hall_state", view.hallSet() ? "已落成" : "未放置");
        set(player, "can_found",
                view.state() == TownState.PENDING && view.memberCount() >= cfg.minMembers() ? "1" : "0");
    }

    private void writeMembers(final Player player, final int page) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final List<Map.Entry<UUID, Role>> ordered = view == null
                ? List.of()
                : view.raw().membersByRole();
        final int pages = Math.max(1, (ordered.size() + MEMBER_ROWS - 1) / MEMBER_ROWS);
        final int now = Math.min(Math.max(1, page), pages);
        set(player, "page_now", String.valueOf(now));
        set(player, "page_total", String.valueOf(pages));
        set(player, "has_prev", now > 1 ? "1" : "0");
        set(player, "has_next", now < pages ? "1" : "0");
        set(player, "prev_page", String.valueOf(now - 1));
        set(player, "next_page", String.valueOf(now + 1));
        final boolean mayor = view != null && view.mayor().equals(player.getUniqueId());
        set(player, "is_mayor", mayor ? "1" : "0");
        for (int i = 0; i < MEMBER_ROWS; i++) {
            final int index = (now - 1) * MEMBER_ROWS + i;
            if (view == null || index >= ordered.size()) {
                set(player, "member_" + i, SLOT_EMPTY);
                set(player, "member_" + i + "_role", null);
                continue;
            }
            final Map.Entry<UUID, Role> entry = ordered.get(index);
            final UUID member = entry.getKey();
            final boolean self = member.equals(player.getUniqueId());
            set(player, "member_" + i, self ? entry.getValue().canManage()
                    ? "&#38BDF8" + nameOf(member) : "&#38BDF8" + nameOf(member) + " &#5A6480(我)"
                    : nameOf(member));
            final Role role = entry.getValue();
            final String roleText = switch (role) {
                case MAYOR -> "&#F0B429镇长";
                case OFFICER -> "&#34D399官员";
                case MEMBER -> "&#8A93B2成员";
            };
            set(player, "member_" + i + "_role", roleText);
        }
    }

    private void writeClaim(final Player player) {
        final ChunkKey center = this.claimCenter.get(player.getUniqueId());
        if (center == null) {
            return;
        }
        final TownSnapshotView own = plugin.service().townOf(player.getUniqueId());
        set(player, "claim_center", center.x() + ", " + center.z());
        final int half = CLAIM_GRID / 2;
        for (int r = 0; r < CLAIM_GRID; r++) {
            for (int c = 0; c < CLAIM_GRID; c++) {
                final ChunkKey key = new ChunkKey(center.world(),
                        center.x() + c - half, center.z() + r - half);
                set(player, "cell_" + r + "_" + c, cellGlyph(key, own));
            }
        }
    }

    /** 每格渲染一个带色块字符（颜色写在内容里，方块字符即色块）。 */
    private String cellGlyph(final ChunkKey key, final TownSnapshotView own) {
        if (!plugin.service().worldAllowed(key.world())) {
            return "&#C08A5A×";
        }
        final TownSnapshotView owner = plugin.service().townAt(key);
        if (owner != null) {
            if (own != null && owner.id().equals(own.id()) && key.equals(own.hallChunk())) {
                return "&#F0B429★";
            }
            if (own != null && owner.id().equals(own.id())) {
                return "&#34D399■";
            }
            return "&#F87171■";
        }
        if (own == null) {
            return "&#3A4260·";
        }
        final int max = plugin.config().maxChunksPerTown();
        if (max > 0 && own.claimCount() >= max) {
            return "&#8A93B2■";
        }
        final boolean diagonal = plugin.config().diagonalConnected();
        for (final ChunkKey claim : own.claims()) {
            if (claim.isAdjacentTo(key, diagonal)) {
                return "&#38BDF8□";
            }
        }
        return "&#3A4260·";
    }

    private void writeBuffs(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final long now = System.currentTimeMillis();
        for (final TownConfig.BuffDef def : plugin.config().buffs()) {
            set(player, "bf_" + def.id() + "_cost", costText(def.cost()));
            final Long expiry = view == null ? null : view.buffs().get(def.id());
            final String state;
            if (expiry == null) {
                state = "&#5A6480未激活";
            } else if (expiry == -1) {
                state = "&#F0B429永久生效";
            } else if (expiry > now) {
                state = "&#34D399" + (expiry - now) / 60000 + " 分钟";
            } else {
                state = "&#5A6480已过期";
            }
            set(player, "bf_" + def.id(), state);
        }
    }

    // ------------------------------------------------------------------ 工具

    private String balanceText(final Player player) {
        final var wallet = plugin.service().wallet();
        if (wallet == null) {
            return "—";
        }
        final String code = wallet.resolveCurrency(plugin.config().currencyCode());
        final Double cached = wallet.cachedBalance(player.getUniqueId(), code);
        if (cached == null) {
            return "查询中";
        }
        return trim(cached) + " " + wallet.symbolOf(plugin.config().currencyCode(), code);
    }

    private String costText(final double amount) {
        final var wallet = plugin.service().wallet();
        final String symbol = wallet == null ? "✦"
                : wallet.symbolOf(plugin.config().currencyCode(),
                        wallet.resolveCurrency(plugin.config().currencyCode()));
        return trim(amount) + (symbol.isBlank() ? "" : " " + symbol);
    }

    private static String trim(final double amount) {
        return amount == Math.floor(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
    }

    private String nameOf(final UUID uuid) {
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "未知" : name;
    }
}
