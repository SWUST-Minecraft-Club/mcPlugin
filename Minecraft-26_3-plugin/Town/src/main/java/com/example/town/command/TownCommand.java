package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.service.TownService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * /town 指令：GUI 的全量等价通道（也服务于无 ArcMenu 环境）。
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
                                            result -> service.deliver(player, result));
                                    return 1;
                                })))
                .then(Commands.literal("found").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.found(player, result -> service.deliver(player, result));
                    return 1;
                }))
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            Player player = needPlayer(plugin, ctx);
                            if (player == null) {
                                return 0;
                            }
                            player.sendMessage(plugin.messages().render("disband-need-confirm"));
                            return 1;
                        })
                        .then(Commands.literal("confirm").executes(ctx -> {
                            Player player = needPlayer(plugin, ctx);
                            if (player == null) {
                                return 0;
                            }
                            service.disband(player, result -> service.deliver(player, result));
                            return 1;
                        })))
                .then(playerArg(plugin, "invite", "invite"))
                .then(Commands.literal("accept").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.acceptInvite(player, result -> service.deliver(player, result));
                    return 1;
                }))
                .then(Commands.literal("reject").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.rejectInvite(player, result -> service.deliver(player, result));
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.leave(player, result -> service.deliver(player, result));
                    return 1;
                }))
                .then(playerArg(plugin, "kick", "kick"))
                .then(playerArg(plugin, "transfer", "transfer"))
                .then(playerArg(plugin, "promote", "promote"))
                .then(playerArg(plugin, "demote", "demote"))
                .then(Commands.literal("info").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    sendInfo(plugin, player, null);
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    sendList(plugin, ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("spawn").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.townSpawn(player, result -> service.deliver(player, result));
                    return 1;
                }))
                .then(Commands.literal("claim").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.claim(player, ChunkKey.of(player.getLocation()),
                            result -> service.deliver(player, result));
                    return 1;
                }))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    service.unclaim(player, ChunkKey.of(player.getLocation()),
                            result -> service.deliver(player, result));
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
                                                    result -> service.deliver(player, result));
                                            return 1;
                                        }))))
                .then(Commands.literal("cancel").executes(ctx -> {
                    Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    plugin.chatInputs().cancel(player);
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

    /** 在线玩家名参数（带补全）。 */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            playerArg(TownPlugin plugin, String literal, String op) {
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
                        case "invite" -> service.invite(player, target,
                                result -> service.deliver(player, result));
                        case "kick" -> service.kick(player, target,
                                result -> service.deliver(player, result));
                        case "transfer" -> service.transfer(player, target,
                                result -> service.deliver(player, result));
                        case "promote" -> service.promote(player, target,
                                result -> service.deliver(player, result));
                        case "demote" -> service.demote(player, target,
                                result -> service.deliver(player, result));
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
                "<gold>/town disband</gold> <gray>解散小镇（confirm 确认）",
                "<gold>/town claim | unclaim</gold> <gray>圈下 / 放弃脚下区块",
                "<gold>/town map</gold> <gray>文本地图",
                "<gold>/town spawn</gold> <gray>回小镇传送点",
                "<gold>/town info | list | buffs</gold> <gray>信息查询",
                "<gold>/town buff buy <id></gold> <gray>购买领地祝福");
        for (String line : lines) {
            to.sendMessage(plugin.messages().renderPlain(line));
        }
    }

    private static void sendInfo(TownPlugin plugin, Player player, String name) {
        TownService service = plugin.service();
        var view = name == null ? service.townOf(player.getUniqueId()) : null;
        if (view == null && name != null) {
            for (var candidate : service.listTowns()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    view = candidate;
                    break;
                }
            }
        }
        if (view == null) {
            player.sendMessage(plugin.messages().render("not-in-town"));
            return;
        }
        String mayorName = org.bukkit.Bukkit.getOfflinePlayer(view.mayor()).getName();
        player.sendMessage(plugin.messages().renderPlain("<gold>─── <white>" + view.name()
                + "</white> ───"));
        player.sendMessage(plugin.messages().renderPlain("<gray>状态：<white>"
                + (view.active() ? "已成立" : "筹备中")));
        player.sendMessage(plugin.messages().renderPlain("<gray>镇长：<white>"
                + (mayorName == null ? "?" : mayorName)));
        player.sendMessage(plugin.messages().renderPlain("<gray>成员：<white>"
                + view.memberCount()));
        player.sendMessage(plugin.messages().renderPlain("<gray>领地：<white>"
                + view.claimCount() + " 块"));
        player.sendMessage(plugin.messages().renderPlain("<gray>祝福：<white>"
                + view.buffs().size() + " 个生效中"));
        player.sendMessage(plugin.messages().renderPlain("<gray>成立于：<white>"
                + view.createdDate()));
    }

    private static void sendList(TownPlugin plugin, org.bukkit.command.CommandSender to) {
        var towns = plugin.service().listTowns();
        if (towns.isEmpty()) {
            to.sendMessage(Component.text("服务器还没有小镇。", NamedTextColor.GRAY));
            return;
        }
        to.sendMessage(plugin.messages().renderPlain(
                "<gold>─── 小镇列表（" + towns.size() + "）───"));
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
        player.sendMessage(plugin.messages().renderPlain("<gold>─── 领地祝福 ───"));
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
        player.sendMessage(plugin.messages().renderPlain("<gold>─── 领地地图 "
                + "<gray>(" + center.x() + ", " + center.z() + ")"));
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
