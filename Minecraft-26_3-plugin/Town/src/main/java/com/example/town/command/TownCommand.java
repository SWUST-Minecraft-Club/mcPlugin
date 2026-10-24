package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.service.TownService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * /town 指令：ArcMenu 界面的全量等价通道（无 ArcMenu 环境下即完整功能），
 * 同时是菜单按钮的后端（按钮动作就是这里的子命令）。
 * 通过 LifecycleEvents.COMMANDS 注册；权限 town.use / town.admin。
 */
public final class TownCommand {

    private TownCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(TownPlugin plugin) {
        TownService service = plugin.service();
        return Commands.literal("town")
                .requires(src -> src.getSender().hasPermission("town.use"))
                // 无子命令：打开图形界面（无 ArcMenu 时给帮助）
                .executes(ctx -> {
                    Player player = player(ctx);
                    if (player == null) {
                        help(plugin, ctx.getSource().getSender());
                        return 1;
                    }
                    plugin.openUi(player);
                    return 1;
                })
                .then(Commands.literal("help").executes(ctx -> {
                    help(plugin, ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Player player = needPlayer(plugin, ctx);
                                    if (player == null) {
                                        return 0;
                                    }
                                    service.create(player, StringArgumentType.getString(ctx, "name"),
                                            feedback(plugin, player));
                                    return 1;
                                })))
                .then(Commands.literal("found").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.found(player, feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("accept").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.acceptInvite(player, feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("reject").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.rejectInvite(player, feedback(plugin, player));
                    return 1;
                }))
                .then(nameArg(plugin, "invite", "invite"))
                .then(nameArg(plugin, "kick", "kick"))
                .then(nameArg(plugin, "transfer", "transfer"))
                .then(nameArg(plugin, "promote", "promote"))
                .then(nameArg(plugin, "demote", "demote"))
                .then(Commands.literal("leave").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.leave(player, feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("disband").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.disband(player, feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("spawn").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.townSpawn(player, feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("claim").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.claim(player, ChunkKey.of(player.getLocation()),
                            feedback(plugin, player));
                    return 1;
                }))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.unclaim(player, ChunkKey.of(player.getLocation()),
                            feedback(plugin, player));
                    return 1;
                }))
                // ---- 菜单后端：圈地地图格（右键购买 / Shift+右键放弃，坐标为格偏移）----
                .then(Commands.literal("claimmap")
                        .then(Commands.argument("row", IntegerArgumentType.integer(0, 8))
                                .then(Commands.argument("col", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> {
                                            Player player = needPlayer(plugin, ctx);
                                            if (player == null) {
                                                return 0;
                                            }
                                            service.claim(player, cellAt(player, ctx),
                                                    feedback(plugin, player));
                                            return 1;
                                        })))
                        .then(Commands.literal("center").executes(ctx -> {
                            Player player = needPlayer(plugin, ctx);
                            if (player == null) {
                                return 0;
                            }
                            if (plugin.ui() != null) {
                                plugin.ui().openClaim(player);
                            }
                            return 1;
                        })))
                .then(Commands.literal("unclaimat")
                        .then(Commands.argument("row", IntegerArgumentType.integer(0, 8))
                                .then(Commands.argument("col", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> {
                                            Player player = needPlayer(plugin, ctx);
                                            if (player == null) {
                                                return 0;
                                            }
                                            service.unclaim(player, cellAt(player, ctx),
                                                    feedback(plugin, player));
                                            return 1;
                                        }))))
                // ---- 菜单后端：成员翻页（重写 meta 并刷新）----
                .then(Commands.literal("members")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    Player player = needPlayer(plugin, ctx);
                                    if (player == null) {
                                        return 0;
                                    }
                                    if (plugin.ui() != null) {
                                        plugin.ui().openMembers(player,
                                                IntegerArgumentType.getInteger(ctx, "page"));
                                    }
                                    return 1;
                                })))
                // ---- 菜单后端：buff 购买 ----
                .then(Commands.literal("buff")
                        .then(Commands.literal("buy")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Player player = needPlayer(plugin, ctx);
                                            if (player == null) {
                                                return 0;
                                            }
                                            service.buyBuff(player,
                                                    StringArgumentType.getString(ctx, "id"),
                                                    feedback(plugin, player));
                                            return 1;
                                        }))))
                // ---- 危险操作二次确认（菜单按钮连点两次即确认）----
                .then(Commands.literal("confirm")
                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Player player = needPlayer(plugin, ctx);
                                    if (player == null) {
                                        return 0;
                                    }
                                    doConfirm(plugin, player,
                                            StringArgumentType.getString(ctx, "action").trim());
                                    return 1;
                                })))
                .then(Commands.literal("info").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    sendInfo(plugin, player);
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    sendList(plugin, ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("map").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    sendChatMap(plugin, player);
                    return 1;
                }))
                .then(Commands.literal("buffs").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    sendBuffs(plugin, player);
                    return 1;
                }))
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission("town.admin"))
                        .then(Commands.literal("reload").executes(ctx -> {
                            plugin.reloadEverything();
                            ctx.getSource().getSender().sendMessage(
                                    plugin.messages().render("reloaded"));
                            return 1;
                        }))
                        .then(Commands.literal("givehall").executes(ctx -> {
                            Player player = needPlayer(plugin, ctx);
                            if (player == null) {
                                return 0;
                            }
                            plugin.townHalls().give(player);
                            player.sendMessage(plugin.messages().render("hall-given"));
                            return 1;
                        })))
                .build();
    }

    // ------------------------------------------------------------------
    // 参数辅助
    // ------------------------------------------------------------------

    /** 在线玩家名参数（带补全），执行对应成员操作。 */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            nameArg(TownPlugin plugin, String literal, String op) {
        return Commands.argument(literal, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (Player online : plugin.getServer().getOnlinePlayers()) {
                        builder.suggest(online.getName());
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    String target = StringArgumentType.getString(ctx, literal);
                    TownService service = plugin.service();
                    switch (op) {
                        case "invite" -> service.invite(player, target, feedback(plugin, player));
                        case "kick" -> service.kick(player, target, feedback(plugin, player));
                        case "transfer" -> service.transfer(player, target, feedback(plugin, player));
                        case "promote" -> service.promote(player, target, feedback(plugin, player));
                        case "demote" -> service.demote(player, target, feedback(plugin, player));
                        default -> {
                        }
                    }
                    return 1;
                });
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player player ? player : null;
    }

    private static Player needPlayer(TownPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            ctx.getSource().getSender().sendMessage(plugin.messages().render("players-only"));
        }
        return player;
    }

    /** 圈地地图格 → 区块键（行/列偏移以玩家当前区块为中心，与 TownUi 写入的格一致）。 */
    private static ChunkKey cellAt(Player player, CommandContext<CommandSourceStack> ctx) {
        ChunkKey center = ChunkKey.of(player.getLocation());
        int row = IntegerArgumentType.getInteger(ctx, "row");
        int col = IntegerArgumentType.getInteger(ctx, "col");
        return new ChunkKey(center.world(), center.x() + col - 4, center.z() + row - 4);
    }

    /** 统一回调：投递结果消息 + 刷新当前菜单的动态值。 */
    private static Consumer<TownService.Result> feedback(TownPlugin plugin, Player player) {
        return result -> {
            plugin.service().deliver(player, result);
            if (plugin.ui() != null) {
                plugin.ui().refresh(player);
            }
        };
    }

    // ------------------------------------------------------------------
    // 危险操作二次确认（菜单按钮连点两次 = 确认）
    // ------------------------------------------------------------------

    private static final Map<UUID, PendingConfirm> PENDING = new ConcurrentHashMap<>();
    private static final long CONFIRM_WINDOW_MS = 60_000L;

    private record PendingConfirm(String action, long expireAt) {
    }

    private static void doConfirm(TownPlugin plugin, Player player, String action) {
        TownService service = plugin.service();
        PendingConfirm pending = PENDING.get(player.getUniqueId());
        boolean armed = pending != null && pending.action().equals(action)
                && pending.expireAt() > System.currentTimeMillis();
        if (!armed) {
            PENDING.put(player.getUniqueId(), new PendingConfirm(action,
                    System.currentTimeMillis() + CONFIRM_WINDOW_MS));
            plugin.messages().send(player, "confirm-armed",
                    Placeholder.unparsed("action", confirmLabel(action)));
            return;
        }
        PENDING.remove(player.getUniqueId());
        Consumer<TownService.Result> fb = feedback(plugin, player);
        String[] parts = action.split(" ");
        String head = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";
        switch (head) {
            case "leave" -> service.leave(player, fb);
            case "disband" -> service.disband(player, fb);
            case "kick" -> service.kick(player, argument, fb);
            case "transfer" -> service.transfer(player, argument, fb);
            default -> plugin.messages().sendRaw(player,
                    "<red>未知的确认操作：" + head);
        }
    }

    private static String confirmLabel(String action) {
        return switch (action.split(" ")[0]) {
            case "leave" -> "退出小镇";
            case "disband" -> "解散小镇";
            case "kick" -> "踢出成员";
            case "transfer" -> "转让镇长";
            default -> action;
        };
    }

    // ------------------------------------------------------------------
    // 文本输出
    // ------------------------------------------------------------------

    private static void help(TownPlugin plugin, org.bukkit.command.CommandSender to) {
        List<String> lines = List.of(
                "<gold>/town</gold> <gray>打开小镇界面",
                "<gold>/town create <名称></gold> <gray>筹备新小镇",
                "<gold>/town found</gold> <gray>成立小镇（成员达标后）",
                "<gold>/town invite <玩家></gold> <gray>邀请成员",
                "<gold>/town accept | reject</gold> <gray>接受 / 拒绝邀请",
                "<gold>/town kick | promote | demote | transfer <玩家></gold> <gray>成员管理",
                "<gold>/town leave</gold> <gray>退出小镇",
                "<gold>/town disband</gold> <gray>解散小镇",
                "<gold>/town claim | unclaim</gold> <gray>圈下 / 放弃脚下区块",
                "<gold>/town map</gold> <gray>文本地图",
                "<gold>/town spawn</gold> <gray>回小镇传送点",
                "<gold>/town info | list | buffs</gold> <gray>信息查询",
                "<gold>/town buff buy <id></gold> <gray>购买领地祝福");
        for (String line : lines) {
            to.sendMessage(plugin.messages().renderPlain(line));
        }
    }

    private static void sendInfo(TownPlugin plugin, Player player) {
        var view = plugin.service().townOf(player.getUniqueId());
        if (view == null) {
            player.sendMessage(plugin.messages().render("not-in-town"));
            return;
        }
        String mayorName = org.bukkit.Bukkit.getOfflinePlayer(view.mayor()).getName();
        player.sendMessage(plugin.messages().renderPlain(
                "<gold>──── <white>" + view.name() + "</white> ────"));
        player.sendMessage(plugin.messages().renderPlain("<gray>状态：<white>"
                + (view.active() ? "已成立" : "筹备中")));
        player.sendMessage(plugin.messages().renderPlain("<gray>镇长：<white>"
                + (mayorName == null ? "?" : mayorName)));
        player.sendMessage(plugin.messages().renderPlain("<gray>成员：<white>" + view.memberCount()));
        player.sendMessage(plugin.messages().renderPlain("<gray>领地：<white>" + view.claimCount() + " 块"));
        player.sendMessage(plugin.messages().renderPlain("<gray>祝福：<white>"
                + view.buffs().size() + " 个生效中"));
        player.sendMessage(plugin.messages().renderPlain("<gray>成立于：<white>" + view.createdDate()));
    }

    private static void sendList(TownPlugin plugin, org.bukkit.command.CommandSender to) {
        var towns = plugin.service().listTowns();
        if (towns.isEmpty()) {
            to.sendMessage(Component.text("服务器还没有小镇。", NamedTextColor.GRAY));
            return;
        }
        to.sendMessage(plugin.messages().renderPlain(
                "<gold>── 小镇列表（" + towns.size() + "）──"));
        for (var view : towns) {
            String mayorName = org.bukkit.Bukkit.getOfflinePlayer(view.mayor()).getName();
            to.sendMessage(plugin.messages().renderPlain("<gray>- <white>" + view.name()
                    + "</white> <dark_gray>·</dark_gray> <gray>镇长 <white>"
                    + (mayorName == null ? "?" : mayorName)
                    + "</white> <dark_gray>·</dark_gray> <gray>" + view.memberCount() + " 人 / "
                    + view.claimCount() + " 块"
                    + (view.active() ? "" : " <yellow>(筹备中)")));
        }
    }

    private static void sendBuffs(TownPlugin plugin, Player player) {
        var view = plugin.service().townOf(player.getUniqueId());
        player.sendMessage(plugin.messages().renderPlain("<gold>── 领地祝福 ──"));
        long now = System.currentTimeMillis();
        for (var def : plugin.config().buffs()) {
            Long expiry = view == null ? null : view.buffs().get(def.id());
            String state;
            if (expiry == null) {
                state = "<gray>未激活";
            } else if (expiry == -1) {
                state = "<light_purple>永久生效";
            } else if (expiry > now) {
                state = "<green>生效中（剩 " + (expiry - now) / 60000 + " 分钟）";
            } else {
                state = "<gray>未激活";
            }
            player.sendMessage(plugin.messages().renderPlain("<gray>- " + def.displayMini()
                    + " <dark_gray>·</dark_gray> <white>" + trim(def.cost())
                    + "</white> <dark_gray>·</dark_gray> " + state
                    + " <dark_gray>/town buff buy " + def.id()));
        }
    }

    /** 11×11 聊天文本地图（以玩家所在区块为中心）。 */
    private static void sendChatMap(TownPlugin plugin, Player player) {
        ChunkKey center = ChunkKey.of(player.getLocation());
        var view = plugin.service().townAt(center);
        player.sendMessage(plugin.messages().renderPlain("<gold>── 领地地图 <gray>("
                + center.x() + ", " + center.z() + ")"));
        for (int dz = -5; dz <= 5; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -5; dx <= 5; dx++) {
                ChunkKey key = new ChunkKey(center.world(), center.x() + dx, center.z() + dz);
                var owner = plugin.service().townAt(key);
                String cell;
                if (owner == null) {
                    cell = "<dark_gray>· ";
                } else if (dx == 0 && dz == 0) {
                    cell = "<yellow>▣ ";
                } else if (view != null && owner.id().equals(view.id())) {
                    cell = "<green>■ ";
                } else {
                    cell = "<red>■ ";
                }
                row.append(cell);
            }
            player.sendMessage(plugin.messages().renderPlain(row.toString().trim()));
        }
        player.sendMessage(plugin.messages().renderPlain(
                "<dark_gray>▣ 你所在 · <green>■ 本镇 · <red>■ 他镇 · <dark_gray>· 荒地"));
    }

    private static String trim(double amount) {
        return amount == Math.floor(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
    }
}
