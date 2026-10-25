package com.example.town.service;

import com.example.town.Messages;
import com.example.town.TownPlugin;
import com.example.town.TownConfig;
import com.example.town.TownRegistry;
import com.example.town.event.TownEvents;
import com.example.town.hook.WalletHook;
import com.example.town.model.ChunkKey;
import com.example.town.model.Role;
import com.example.town.model.Town;
import com.example.town.model.TownState;
import com.example.town.model.TownSnapshotView;
import com.example.town.store.TownStore;
import com.example.town.util.Threads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 小镇全部业务操作的唯一入口。
 * 约定：
 * - 所有状态变更持 {@link TownRegistry#guard()} 全局锁；
 * - 扣费走 Wallet 异步，回调回来后重验状态再落账，失败尽力退款；
 * - 每个操作都接收 {@code Consumer<Result>}，回调保证在玩家所属区域线程执行。
 */
public final class TownService {

    /** 操作结果：ok + 消息键 + 占位符（消息键见 config.yml messages 段）。 */
    public record Result(boolean ok, String messageKey, TagResolver[] resolvers) {
        public static Result ok(String key, TagResolver... resolvers) {
            return new Result(true, key, resolvers);
        }

        public static Result fail(String key, TagResolver... resolvers) {
            return new Result(false, key, resolvers);
        }
    }

    private final TownPlugin plugin;
    private final TownRegistry registry;
    private final TownStore store;
    private final Messages messages;
    private final TownEvents events;
    private final WalletHook wallet;

    /** 扣费在途标记：防双击重复扣费。 */
    private final Map<UUID, Boolean> charging = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);

    public TownService(TownPlugin plugin, TownRegistry registry, TownStore store,
                       Messages messages, TownEvents events, WalletHook wallet) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.messages = messages;
        this.events = events;
        this.wallet = wallet;
    }

    public TownRegistry registry() {
        return registry;
    }

    public Messages messages() {
        return messages;
    }

    public WalletHook wallet() {
        return wallet;
    }

    /** 把结果送到玩家区域线程（指令与 GUI 回调统一走这里）。 */
    public void deliver(Player player, Result result) {
        Threads.forPlayer(plugin, player, () ->
                player.sendMessage(messages.render(result.messageKey(), result.resolvers())));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public TownSnapshotView townOf(UUID playerId) {
        return TownSnapshotView.of(registry.snapshotOfMember(playerId));
    }

    /** 该玩家手上的待处理邀请所在小镇（无则 null）。 */
    public TownSnapshotView inviteOf(UUID playerId) {
        return TownSnapshotView.of(registry.snapshotOfInvited(playerId));
    }

    public TownSnapshotView townAt(ChunkKey key) {
        return TownSnapshotView.of(registry.snapshotOfChunk(key));
    }

    public List<TownSnapshotView> listTowns() {
        List<TownSnapshotView> list = new ArrayList<>();
        for (var snapshot : registry.snapshots()) {
            list.add(TownSnapshotView.of(snapshot));
        }
        return list;
    }

    /** 是否需要拦截该玩家对该位置的操作（领地存在 + 已成立 + 非成员 + 无 bypass）。 */
    public boolean denied(Player player, Location location) {
        TownSnapshotView view = townAt(ChunkKey.of(location));
        if (view == null || !view.active()) {
            return false;
        }
        if (view.members().containsKey(player.getUniqueId())) {
            return false;
        }
        return !player.hasPermission("town.bypass");
    }

    // ------------------------------------------------------------------
    // 创建 / 成立 / 解散
    // ------------------------------------------------------------------

    /** 校验小镇名（长度 / 字符 / 重名前置检查）；返回 null 表示通过。 */
    public Result validateName(String name) {
        TownConfig cfg = plugin.config();
        if (name == null || name.isBlank()) {
            return Result.fail("name-invalid");
        }
        if (name.length() < cfg.nameMinLength()) {
            return Result.fail("name-too-short",
                    Placeholder.unparsed("min", String.valueOf(cfg.nameMinLength())));
        }
        if (name.length() > cfg.nameMaxLength()) {
            return Result.fail("name-too-long",
                    Placeholder.unparsed("max", String.valueOf(cfg.nameMaxLength())));
        }
        if (!cfg.nameRegex().matcher(name).matches()) {
            return Result.fail("name-invalid");
        }
        return null;
    }

    public void create(Player founder, String name, Consumer<Result> fb) {
        Result invalid = validateName(name);
        if (invalid != null) {
            fb.accept(invalid);
            return;
        }
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town existing = registry.byMember(founder.getUniqueId());
            if (existing != null) {
                fb.accept(Result.fail("in-town-already",
                        Placeholder.unparsed("town", existing.name())));
                return;
            }
            if (registry.byName(name) != null) {
                fb.accept(Result.fail("town-exists", Placeholder.unparsed("name", name)));
                return;
            }
            Town invited = registry.byInvited(founder.getUniqueId());
            if (invited != null) {
                invited.invites().remove(founder.getUniqueId());
                registry.unindexInvite(founder.getUniqueId());
            }
            Town town = new Town(newTownId(), name, founder.getUniqueId(),
                    System.currentTimeMillis());
            registry.register(town);
            requestSave();
            fb.accept(Result.ok("created",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("min", String.valueOf(plugin.config().minMembers()))));
        }
    }

    public void found(Player mayor, Consumer<Result> fb) {
        String townId;
        String townName;
        int memberCount;
        double cost;
        String currency;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(mayor.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (town.state() == TownState.ACTIVE) {
                fb.accept(Result.fail("already-active", Placeholder.unparsed("town", town.name())));
                return;
            }
            if (!town.mayor().equals(mayor.getUniqueId())) {
                fb.accept(Result.fail("transfer-must-mayor"));
                return;
            }
            memberCount = town.members().size();
            if (memberCount < plugin.config().minMembers()) {
                fb.accept(Result.fail("found-min-members",
                        Placeholder.unparsed("min", String.valueOf(plugin.config().minMembers())),
                        Placeholder.unparsed("count", String.valueOf(memberCount))));
                return;
            }
            townId = town.id();
            townName = town.name();
            cost = plugin.config().foundingCost();
            currency = wallet == null ? "" : wallet.resolveCurrency(plugin.config().currencyCode());
            if (cost > 0 && (wallet == null || currency.isBlank())) {
                fb.accept(Result.fail("wallet-missing"));
                return;
            }
        }
        if (cost > 0) {
            if (charging.putIfAbsent(mayor.getUniqueId(), true) != null) {
                fb.accept(Result.fail("action-busy"));
                return;
            }
            String operationId = UUID.randomUUID().toString();
            wallet.debit(mayor.getUniqueId(), mayor.getName(), currency, cost,
                            "town-found:" + townId + ":" + operationId,
                            "成立小镇 " + townName)
                    .whenComplete((pay, ex) -> afterWallet(mayor, () -> {
                        charging.remove(mayor.getUniqueId());
                        if (pay == null || !pay.ok()) {
                            fb.accept(Result.fail("wallet-failed"));
                            return;
                        }
                        if (activate(mayor, townId, cost)) {
                            fb.accept(Result.ok("founded", Placeholder.unparsed("name", townName)));
                        } else {
                            refund(mayor, currency, cost, "town-found-refund:" + townId + ":" + operationId);
                            fb.accept(Result.fail("action-state-changed"));
                        }
                    }));
        } else if (activate(mayor, townId, 0)) {
            fb.accept(Result.ok("founded", Placeholder.unparsed("name", townName)));
        } else {
            fb.accept(Result.fail("action-state-changed"));
        }
    }

    /** 扣款后重验小镇、镇长和人数，再置为 ACTIVE。 */
    private boolean activate(Player mayor, String townId, double charged) {
        String townName;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byId(townId);
            if (town == null || town.state() != TownState.PENDING
                    || !town.mayor().equals(mayor.getUniqueId())
                    || town.members().size() < plugin.config().minMembers()) {
                return false;
            }
            town.state(TownState.ACTIVE);
            townName = town.name();
            requestSave();
        }
        if (charged > 0) {
            notify(mayor.getUniqueId(), () -> messages.render("claim-charged",
                    Placeholder.unparsed("amount", trimAmount(charged)),
                    Placeholder.unparsed("symbol", wallet.symbolOf(
                            plugin.config().currencyCode(),
                            wallet.resolveCurrency(plugin.config().currencyCode())))));
        }
        Threads.forPlayer(plugin, mayor, () -> plugin.townHalls().give(mayor));
        Component broadcast = messages.render("found-broadcast",
                Placeholder.unparsed("player", mayor.getName()),
                Placeholder.unparsed("name", townName));
        Bukkit.broadcast(broadcast);
        events.fireFounded(mayor.getUniqueId(), townId, townName);
        return true;
    }

    public void disband(Player actor, Consumer<Result> fb) {
        List<UUID> members;
        String name;
        String townId;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(actor.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("disband-no-town"));
                return;
            }
            if (!town.mayor().equals(actor.getUniqueId()) && !actor.hasPermission("town.admin")) {
                fb.accept(Result.fail("transfer-must-mayor"));
                return;
            }
            name = town.name();
            townId = town.id();
            members = List.copyOf(town.members().keySet());
            registry.unregister(townId);
            requestSave();
        }
        fb.accept(Result.ok("disbanded", Placeholder.unparsed("name", name)));
        Component notice = messages.render("disbanded", Placeholder.unparsed("name", name));
        for (UUID member : members) {
            notify(member, () -> notice);
        }
        events.fireDisbanded(townId, name);
    }

    // ------------------------------------------------------------------
    // 邀请与成员
    // ------------------------------------------------------------------

    public void invite(Player manager, String targetName, Consumer<Result> fb) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            fb.accept(Result.fail("target-not-online"));
            return;
        }
        if (target.getUniqueId().equals(manager.getUniqueId())) {
            fb.accept(Result.fail("invite-self"));
            return;
        }
        String townName;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(manager.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            Role role = town.members().get(manager.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            if (registry.byMember(target.getUniqueId()) != null) {
                fb.accept(Result.fail("invite-target-has-town"));
                return;
            }
            if (registry.byInvited(target.getUniqueId()) != null) {
                fb.accept(Result.fail("invite-target-invited"));
                return;
            }
            int max = plugin.config().maxMembers();
            if (max > 0 && town.members().size() + town.invites().size() >= max) {
                fb.accept(Result.fail("invite-town-full",
                        Placeholder.unparsed("max", String.valueOf(max))));
                return;
            }
            town.invites().put(target.getUniqueId(),
                    System.currentTimeMillis() + plugin.config().inviteExpireMillis());
            registry.indexInvite(target.getUniqueId(), town.id());
            requestSave();
            townName = town.name();
            fb.accept(Result.ok("invite-sent", Placeholder.unparsed("player", target.getName())));
        }
        MiniMessage mini = MiniMessage.miniMessage();
        Component accept = mini.deserialize(messages.mini("invited-you-accept"))
                .clickEvent(ClickEvent.runCommand("/town accept"))
                .hoverEvent(HoverEvent.showText(Component.text("加入该小镇", NamedTextColor.GREEN)));
        Component reject = mini.deserialize(messages.mini("invited-you-reject"))
                .clickEvent(ClickEvent.runCommand("/town reject"))
                .hoverEvent(HoverEvent.showText(Component.text("拒绝邀请", NamedTextColor.RED)));
        Component letter = messages.render("invited-you",
                Placeholder.unparsed("town", townName))
                .append(Component.space())
                .append(accept)
                .append(Component.space())
                .append(reject);
        Threads.forPlayer(plugin, target, () -> target.sendMessage(letter));
    }

    public void acceptInvite(Player player, Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byInvited(player.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("invite-none"));
                return;
            }
            Long expiry = town.invites().get(player.getUniqueId());
            if (expiry == null || expiry <= System.currentTimeMillis()) {
                town.invites().remove(player.getUniqueId());
                registry.unindexInvite(player.getUniqueId());
                requestSave();
                fb.accept(Result.fail("invite-expired"));
                return;
            }
            Town existing = registry.byMember(player.getUniqueId());
            if (existing != null) {
                town.invites().remove(player.getUniqueId());
                registry.unindexInvite(player.getUniqueId());
                requestSave();
                fb.accept(Result.fail("in-town-already",
                        Placeholder.unparsed("town", existing.name())));
                return;
            }
            int max = plugin.config().maxMembers();
            if (max > 0 && town.members().size() >= max) {
                town.invites().remove(player.getUniqueId());
                registry.unindexInvite(player.getUniqueId());
                requestSave();
                fb.accept(Result.fail("invite-town-full",
                        Placeholder.unparsed("max", String.valueOf(max))));
                return;
            }
            town.invites().remove(player.getUniqueId());
            registry.unindexInvite(player.getUniqueId());
            town.members().put(player.getUniqueId(), Role.MEMBER);
            registry.indexMember(player.getUniqueId(), town.id());
            requestSave();
            fb.accept(Result.ok("invite-accepted",
                    Placeholder.unparsed("player", player.getName())));
            broadcastToMembers(town.id(), messages.render("invite-accepted",
                    Placeholder.unparsed("player", player.getName())));
        }
    }

    public void rejectInvite(Player player, Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byInvited(player.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("invite-none"));
                return;
            }
            town.invites().remove(player.getUniqueId());
            registry.unindexInvite(player.getUniqueId());
            requestSave();
            fb.accept(Result.ok("invite-rejected",
                    Placeholder.unparsed("player", player.getName())));
        }
    }

    public void leave(Player player, Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(player.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (town.mayor().equals(player.getUniqueId())) {
                fb.accept(Result.fail("mayor-cannot-leave"));
                return;
            }
            String townName = town.name();
            town.members().remove(player.getUniqueId());
            registry.unindexMember(player.getUniqueId());
            requestSave();
            fb.accept(Result.ok("left", Placeholder.unparsed("town", townName)));
            broadcastToMembers(town.id(), messages.render("left-broadcast",
                    Placeholder.unparsed("player", player.getName())));
        }
    }

    public void kick(Player manager, String targetName, Consumer<Result> fb) {
        Player target = Bukkit.getPlayerExact(targetName);
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(manager.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            Role role = town.members().get(manager.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            UUID targetId = target != null
                    ? target.getUniqueId()
                    : findMemberIdByName(town, targetName);
            if (targetId == null || targetId.equals(town.mayor())
                    || !town.members().containsKey(targetId)) {
                fb.accept(Result.fail("target-not-member"));
                return;
            }
            String townName = town.name();
            String targetDisplay = nameOf(target, targetId);
            town.members().remove(targetId);
            registry.unindexMember(targetId);
            requestSave();
            fb.accept(Result.ok("kicked", Placeholder.unparsed("player", targetDisplay)));
            notify(targetId, () -> messages.render("kicked-you",
                    Placeholder.unparsed("town", townName)));
            broadcastToMembers(town.id(), messages.render("kicked-broadcast",
                    Placeholder.unparsed("player", targetDisplay)));
        }
    }

    public void transfer(Player mayor, String targetName, Consumer<Result> fb) {
        Player target = Bukkit.getPlayerExact(targetName);
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(mayor.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (!town.mayor().equals(mayor.getUniqueId())) {
                fb.accept(Result.fail("transfer-must-mayor"));
                return;
            }
            UUID targetId = target != null ? target.getUniqueId() : findMemberIdByName(town, targetName);
            if (targetId == null || !town.members().containsKey(targetId)) {
                fb.accept(Result.fail("target-not-member"));
                return;
            }
            if (targetId.equals(mayor.getUniqueId())) {
                fb.accept(Result.fail("already-role"));
                return;
            }
            town.mayor(targetId);
            requestSave();
            String townName = town.name();
            String targetDisplay = nameOf(target, targetId);
            fb.accept(Result.ok("transfer-done", Placeholder.unparsed("player", targetDisplay)));
            notify(targetId, () -> messages.render("transfer-target",
                    Placeholder.unparsed("town", townName)));
        }
    }

    public void promote(Player manager, String targetName, Consumer<Result> fb) {
        changeRole(manager, targetName, true, fb);
    }

    public void demote(Player manager, String targetName, Consumer<Result> fb) {
        changeRole(manager, targetName, false, fb);
    }

    private void changeRole(Player manager, String targetName, boolean promote,
                            Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(manager.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            Role role = town.members().get(manager.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            UUID targetId = findMemberIdByName(town, targetName);
            if (targetId == null || targetId.equals(town.mayor())) {
                fb.accept(Result.fail("target-not-member"));
                return;
            }
            Role current = town.members().get(targetId);
            String display = nameOf(null, targetId);
            if (promote) {
                if (current != Role.MEMBER) {
                    fb.accept(Result.fail("already-role"));
                    return;
                }
                town.members().put(targetId, Role.OFFICER);
                requestSave();
                fb.accept(Result.ok("promote-done", Placeholder.unparsed("player", display)));
            } else {
                if (current != Role.OFFICER) {
                    fb.accept(Result.fail("already-role"));
                    return;
                }
                town.members().put(targetId, Role.MEMBER);
                requestSave();
                fb.accept(Result.ok("demote-done", Placeholder.unparsed("player", display)));
            }
        }
    }

    // ------------------------------------------------------------------
    // 圈地
    // ------------------------------------------------------------------

    public void claim(Player actor, ChunkKey key, Consumer<Result> fb) {
        if (!worldAllowed(key.world())) {
            fb.accept(Result.fail("claim-world-denied"));
            return;
        }
        String townId;
        double price;
        String currency;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(actor.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (town.state() != TownState.ACTIVE) {
                fb.accept(Result.fail("must-be-active"));
                return;
            }
            Role role = town.members().get(actor.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            Town existing = registry.byChunk(key);
            if (existing != null) {
                fb.accept(Result.fail(existing.id().equals(town.id())
                        ? "claim-already-yours" : "claim-protected-other"));
                return;
            }
            if (!town.claims().isEmpty() && !hasAdjacentClaim(town, key)) {
                fb.accept(Result.fail("claim-adjacent-required"));
                return;
            }
            int maxChunks = plugin.config().maxChunksPerTown();
            if (maxChunks > 0 && town.claims().size() >= maxChunks) {
                fb.accept(Result.fail("claim-max-reached",
                        Placeholder.unparsed("max", String.valueOf(maxChunks))));
                return;
            }
            townId = town.id();
            price = plugin.config().pricePerChunk();
            currency = wallet == null ? "" : wallet.resolveCurrency(plugin.config().currencyCode());
            if (price > 0 && (wallet == null || currency.isBlank())) {
                fb.accept(Result.fail("wallet-missing"));
                return;
            }
        }
        if (price > 0) {
            if (charging.putIfAbsent(actor.getUniqueId(), true) != null) {
                fb.accept(Result.fail("action-busy"));
                return;
            }
            String symbol = wallet.symbolOf(plugin.config().currencyCode(), currency);
            String operationId = UUID.randomUUID().toString();
            wallet.debit(actor.getUniqueId(), actor.getName(), currency, price,
                            "town-claim:" + townId + ":" + key.encode() + ":" + operationId,
                            "小镇圈地 " + key.encode())
                    .whenComplete((pay, ex) -> afterWallet(actor, () -> {
                        charging.remove(actor.getUniqueId());
                        if (pay == null || !pay.ok()) {
                            fb.accept(Result.fail("wallet-failed"));
                            return;
                        }
                        if (applyClaim(actor, townId, key, fb)) {
                            notify(actor.getUniqueId(), () -> messages.render("claim-charged",
                                    Placeholder.unparsed("amount", trimAmount(price)),
                                    Placeholder.unparsed("symbol", symbol)));
                        } else {
                            refund(actor, currency, price,
                                    "town-claim-refund:" + townId + ":" + key.encode() + ":" + operationId);
                        }
                    }));
        } else {
            applyClaim(actor, townId, key, fb);
        }
    }

    /** 落账前按原小镇重新检查操作者、世界、相邻、上限和占用。 */
    private boolean applyClaim(Player actor, String townId, ChunkKey key, Consumer<Result> fb) {
        String townName;
        int claimCount;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(actor.getUniqueId());
            Role role = town == null ? null : town.members().get(actor.getUniqueId());
            if (town == null || !town.id().equals(townId) || town.state() != TownState.ACTIVE
                    || role == null || !role.canManage() || !worldAllowed(key.world())
                    || registry.byChunk(key) != null
                    || (!town.claims().isEmpty() && !hasAdjacentClaim(town, key))) {
                fb.accept(Result.fail("action-state-changed"));
                return false;
            }
            int maxChunks = plugin.config().maxChunksPerTown();
            if (maxChunks > 0 && town.claims().size() >= maxChunks) {
                fb.accept(Result.fail("claim-max-reached",
                        Placeholder.unparsed("max", String.valueOf(maxChunks))));
                return false;
            }
            town.claims().add(key);
            registry.indexClaim(key, town.id());
            requestSave();
            townName = town.name();
            claimCount = town.claims().size();
        }
        fb.accept(Result.ok("claim-success", Placeholder.unparsed("count", String.valueOf(claimCount))));
        events.fireClaim(townId, townName, actor.getUniqueId(), key);
        return true;
    }

    private void refund(Player player, String currency, double amount, String businessNo) {
        if (wallet == null) {
            return;
        }
        wallet.credit(player.getUniqueId(), player.getName(), currency, amount,
                        businessNo, "操作失败退款")
                .thenAccept(pay -> {
                    if (pay == null || !pay.ok()) {
                        plugin.getLogger().severe("Town 退款失败：player=" + player.getUniqueId()
                                + ", amount=" + amount + ", currency=" + currency
                                + ", businessNo=" + businessNo);
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("Town 退款调用异常：player=" + player.getUniqueId()
                            + ", businessNo=" + businessNo + ", error=" + e.getMessage());
                    return null;
                });
    }

    public void unclaim(Player actor, ChunkKey key, Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(actor.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            Role role = town.members().get(actor.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            if (town.isHallSet() && key.equals(town.hallChunk())) {
                fb.accept(Result.fail("unclaim-hall-blocked"));
                return;
            }
            if (!town.claims().remove(key)) {
                fb.accept(Result.fail("claim-protected-other"));
                return;
            }
            registry.unindexClaim(key);
            requestSave();
            fb.accept(Result.ok("unclaimed"));
            events.fireUnclaim(town.id(), town.name(), actor.getUniqueId(), key);
        }
    }

    /** 放置镇政厅：设中心 + 传送点 + 免费圈地（赠送部分无视相连与上限）。 */
    public void placeHall(Player player, Block block, Consumer<Result> fb) {
        int granted;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(player.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (town.state() != TownState.ACTIVE) {
                fb.accept(Result.fail("hall-pending"));
                return;
            }
            if (!town.mayor().equals(player.getUniqueId())) {
                fb.accept(Result.fail("hall-must-mayor"));
                return;
            }
            if (!block.getType().isAir()) {
                fb.accept(Result.fail("hall-target-blocked"));
                return;
            }
            if (!worldAllowed(block.getWorld().getName())) {
                fb.accept(Result.fail("hall-world-denied"));
                return;
            }
            if (town.isHallSet()) {
                fb.accept(Result.fail("hall-exists"));
                return;
            }
            Town owner = registry.byChunk(ChunkKey.of(block.getLocation()));
            if (owner != null && !owner.id().equals(town.id())) {
                fb.accept(Result.fail("claim-protected-other"));
                return;
            }
            block.setType(plugin.config().hallMaterial());
            float yaw = player.getLocation().getYaw() + 180.0f;
            town.hall(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), yaw, 0.0f);
            int radius = plugin.config().freeClaimRadius();
            ChunkKey center = town.hallChunk();
            granted = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkKey key = new ChunkKey(center.world(), center.x() + dx, center.z() + dz);
                    if (registry.byChunk(key) != null) {
                        continue;
                    }
                    town.claims().add(key);
                    registry.indexClaim(key, town.id());
                    granted++;
                    events.fireClaim(town.id(), town.name(), player.getUniqueId(), key);
                }
            }
            requestSave();
            fb.accept(Result.ok("hall-placed"));
            if (granted > 0) {
                player.sendMessage(messages.render("hall-free-claims",
                        Placeholder.unparsed("count", String.valueOf(granted))));
            }
        }
    }

    /** 只清除被挖方块实际对应的镇政厅，不能按破坏者当前小镇猜目标。 */
    public void breakHall(Player player, Block block, Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byChunk(ChunkKey.of(block.getLocation()));
            if (town == null || !town.isHallSet()
                    || !town.hallWorld().equals(block.getWorld().getName())
                    || town.hallX() != block.getX() || town.hallY() != block.getY()
                    || town.hallZ() != block.getZ()) {
                fb.accept(Result.fail("hall-not-set"));
                return;
            }
            if (!town.mayor().equals(player.getUniqueId())) {
                fb.accept(Result.fail("hall-must-mayor"));
                return;
            }
            town.clearHall();
            requestSave();
            fb.accept(Result.ok("hall-broken"));
        }
        Threads.forPlayer(plugin, player, () -> plugin.townHalls().give(player));
    }

    public void townSpawn(Player player, Consumer<Result> fb) {
        TownSnapshotView view = townOf(player.getUniqueId());
        if (view == null) {
            fb.accept(Result.fail("not-in-town"));
            return;
        }
        if (!view.spawnSet()) {
            fb.accept(Result.fail("hall-not-set"));
            return;
        }
        World world = Bukkit.getWorld(view.spawnWorld());
        if (world == null) {
            fb.accept(Result.fail("hall-not-set"));
            return;
        }
        Location loc = new Location(world, view.spawnX(), view.spawnY(), view.spawnZ(),
                view.spawnYaw(), view.spawnPitch());
        player.teleportAsync(loc).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                deliver(player, Result.ok("spawn-teleported"));
            }
        });
    }

    // ------------------------------------------------------------------
    // buff
    // ------------------------------------------------------------------

    public void buyBuff(Player manager, String buffId, Consumer<Result> fb) {
        TownConfig.BuffDef def = null;
        for (TownConfig.BuffDef candidate : plugin.config().buffs()) {
            if (candidate.id().equals(buffId)) {
                def = candidate;
                break;
            }
        }
        if (def == null) {
            fb.accept(Result.fail("buff-not-found"));
            return;
        }
        TownConfig.BuffDef definition = def;
        String townId;
        String currency;
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(manager.getUniqueId());
            if (town == null) {
                fb.accept(Result.fail("not-in-town"));
                return;
            }
            if (town.state() != TownState.ACTIVE) {
                fb.accept(Result.fail("must-be-active"));
                return;
            }
            Role role = town.members().get(manager.getUniqueId());
            if (role == null || !role.canManage()) {
                fb.accept(Result.fail("invite-must-member"));
                return;
            }
            Long expiry = town.buffs().get(buffId);
            if (expiry != null && expiry == -1) {
                fb.accept(Result.fail("buff-active"));
                return;
            }
            townId = town.id();
            currency = wallet == null ? "" : wallet.resolveCurrency(plugin.config().currencyCode());
            if (definition.cost() > 0 && (wallet == null || currency.isBlank())) {
                fb.accept(Result.fail("wallet-missing"));
                return;
            }
        }
        if (definition.cost() > 0) {
            if (charging.putIfAbsent(manager.getUniqueId(), true) != null) {
                fb.accept(Result.fail("action-busy"));
                return;
            }
            String symbol = wallet.symbolOf(plugin.config().currencyCode(), currency);
            String operationId = UUID.randomUUID().toString();
            wallet.debit(manager.getUniqueId(), manager.getName(), currency, definition.cost(),
                            "town-buff:" + townId + ":" + buffId + ":" + operationId,
                            "购买领地祝福 " + buffId)
                    .whenComplete((pay, ex) -> afterWallet(manager, () -> {
                        charging.remove(manager.getUniqueId());
                        if (pay == null || !pay.ok()) {
                            fb.accept(Result.fail("wallet-failed"));
                            return;
                        }
                        if (activateBuff(manager, townId, definition, fb)) {
                            fb.accept(Result.ok("buff-charged",
                                    Placeholder.unparsed("name", definition.displayMini()),
                                    Placeholder.unparsed("amount", trimAmount(definition.cost())),
                                    Placeholder.unparsed("symbol", symbol)));
                        } else {
                            refund(manager, currency, definition.cost(),
                                    "town-buff-refund:" + townId + ":" + buffId + ":" + operationId);
                        }
                    }));
        } else if (activateBuff(manager, townId, definition, fb)) {
            fb.accept(Result.ok("buff-charged",
                    Placeholder.unparsed("name", definition.displayMini()),
                    Placeholder.unparsed("amount", "0"),
                    Placeholder.unparsed("symbol", "")));
        }
    }

    /** 永久祝福重复购买和付款期间换镇/失权都在落账前拦截。 */
    private boolean activateBuff(Player manager, String townId, TownConfig.BuffDef def,
                                 Consumer<Result> fb) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byMember(manager.getUniqueId());
            Role role = town == null ? null : town.members().get(manager.getUniqueId());
            if (town == null || !town.id().equals(townId) || town.state() != TownState.ACTIVE
                    || role == null || !role.canManage()) {
                fb.accept(Result.fail("action-state-changed"));
                return false;
            }
            Long existing = town.buffs().get(def.id());
            if (existing != null && existing == -1) {
                fb.accept(Result.fail("buff-active"));
                return false;
            }
            long base = existing != null && existing > System.currentTimeMillis()
                    ? existing : System.currentTimeMillis();
            town.buffs().put(def.id(), def.permanent() ? -1L : base + def.durationMillis());
            requestSave();
        }
        events.fireBuffActivate(townId, def.id());
        return true;
    }

    // ------------------------------------------------------------------
    // 维护（邀请过期 / 筹备超时 / 过期 buff 惰性清理）
    // ------------------------------------------------------------------

    public void maintenance() {
        long now = System.currentTimeMillis();
        List<String> expiredPending = new ArrayList<>();
        List<String> expiredNames = new ArrayList<>();
        try (TownRegistry.Guard ignored = registry.guard()) {
            long pendingExpire = plugin.config().pendingExpireMillis();
            for (Town town : registry.all()) {
                town.invites().entrySet().removeIf(e -> {
                    boolean gone = e.getValue() <= now;
                    if (gone) {
                        registry.unindexInvite(e.getKey());
                    }
                    return gone;
                });
                town.buffs().entrySet().removeIf(e -> e.getValue() != -1 && e.getValue() <= now);
                if (town.state() == TownState.PENDING
                        && pendingExpire > 0
                        && now - town.createdAt() > pendingExpire) {
                    expiredPending.add(town.id());
                    expiredNames.add(town.name());
                }
            }
            // 在遍历结束后再注销，避免在 registry.all() 上迭代时改结构
            for (String id : expiredPending) {
                registry.unregister(id);
            }
            if (!expiredPending.isEmpty()) {
                requestSave();
            }
        }
        for (int i = 0; i < expiredPending.size(); i++) {
            Bukkit.broadcast(messages.render("pending-expired",
                    Placeholder.unparsed("name", expiredNames.get(i))));
            events.fireDisbanded(expiredPending.get(i), expiredNames.get(i));
        }
    }

    /** 合并落盘：100 tick 内的多次变更只触发一次异步保存。 */
    public void requestSave() {
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        Threads.globalLater(plugin, 100, () -> {
            saveQueued.set(false);
            store.saveAllAsync(registry);
        });
    }

    /** 关服时的同步落盘。 */
    public void saveNow() {
        saveQueued.set(false);
        store.saveAll(registry);
    }

    /** Wallet 的 HTTP 回调不能直接发同步事件；离线玩家也要完成提交或退款。 */
    private void afterWallet(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else if (player.isOnline()) {
            player.getScheduler().run(plugin, t -> task.run(), () -> Threads.global(plugin, task));
        } else {
            Threads.global(plugin, task);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    public boolean worldAllowed(String worldName) {
        var worlds = plugin.config().claimWorlds();
        return worlds.isEmpty() || worlds.contains(worldName);
    }

    private boolean hasAdjacentClaim(Town town, ChunkKey key) {
        boolean diagonal = plugin.config().diagonalConnected();
        for (ChunkKey claim : town.claims()) {
            if (claim.isAdjacentTo(key, diagonal)) {
                return true;
            }
        }
        return false;
    }

    private String newTownId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (registry.byId(id) != null);
        return id;
    }

    private UUID findMemberIdByName(Town town, String name) {
        for (UUID id : town.members().keySet()) {
            String memberName = Bukkit.getOfflinePlayer(id).getName();
            if (memberName != null && memberName.equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    private String nameOf(Player online, UUID id) {
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "未知玩家" : name;
    }

    private void notify(UUID playerId, java.util.function.Supplier<Component> message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Component component = message.get();
            Threads.forPlayer(plugin, player, () -> player.sendMessage(component));
        }
    }

    private void broadcastToMembers(String townId, Component message) {
        try (TownRegistry.Guard ignored = registry.guard()) {
            Town town = registry.byId(townId);
            if (town == null) {
                return;
            }
            for (UUID memberId : town.members().keySet()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    Threads.forPlayer(plugin, member, () -> member.sendMessage(message));
                }
            }
        }
    }

    private static String trimAmount(double amount) {
        return amount == Math.floor(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
    }
}
