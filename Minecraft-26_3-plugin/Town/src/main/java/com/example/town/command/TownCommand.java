package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.model.TownSnapshotView;
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

/** /town 同时提供无 ArcMenu 的完整指令通道和菜单后端。 */
public final class TownCommand {

    private TownCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(final TownPlugin plugin) {
        final TownService service = plugin.service();
        return Commands.literal("town")
                .requires(src -> src.getSender().hasPermission("town.use"))
                .executes(ctx -> {
                    final Player player = player(ctx);
                    if (player == null) {
                        help(plugin, ctx.getSource().getSender());
                    } else {
                        plugin.openUi(player);
                    }
                    return 1;
                })
                .then(Commands.literal("help").executes(ctx -> {
                    help(plugin, ctx.getSource().getSender());
                    return 1;
                }))
                .then(PlotCommands.create(plugin))
                .then(CivicCommands.create(plugin))
                .then(MoveCommands.create(plugin))
                .then(MarketCommands.create(plugin))
                .then(Commands.literal("manage").executes(ctx->{Player p=player(ctx);if(p!=null && plugin.ui()!=null) plugin.ui().operation(p,"civic");return 1;})
                        .then(Commands.argument("operation",StringArgumentType.greedyString()).executes(ctx->{
                            Player p=player(ctx);if(p!=null) {if(plugin.ui()==null) plugin.messages().send(p,"arcmenu-missing");else plugin.ui().operation(p,StringArgumentType.getString(ctx,"operation"));}return 1;
                        })))
                .then(Commands.literal("hall").executes(ctx->{
                    final Player player=needPlayer(plugin,ctx);
                    if(player!=null) {
                        var town=plugin.service().townOf(player.getUniqueId());
                        if(town==null) plugin.messages().send(player,"not-in-town");
                        else if(!town.mayor().equals(player.getUniqueId())) plugin.messages().send(player,"hall-must-mayor");
                        else if(!town.active()) plugin.messages().send(player,"hall-pending");
                        else if(town.hallSet()) plugin.messages().send(player,"hall-exists");
                        else plugin.townHalls().give(player);
                    }
                    return 1;
                }))
                .then(Commands.literal("select")
                        .executes(ctx->{if(player(ctx)!=null) plugin.selections().giveTool(player(ctx));return 1;})
                        .then(Commands.argument("action",StringArgumentType.word()).executes(ctx->{
                            Player p=player(ctx);
                            if(p==null) return 0;
                            String action=StringArgumentType.getString(ctx,"action");
                            try {
                                switch(action) {
                                    case "pos1" -> plugin.selections().selectTarget(p,1);
                                    case "pos2" -> plugin.selections().selectTarget(p,2);
                                    case "clear" -> { plugin.selections().clear(p.getUniqueId());plugin.messages().send(p,"selection-cleared"); }
                                    default -> plugin.messages().send(p,"selection-help");
                                }
                            } catch(IllegalArgumentException e) { plugin.messages().send(p,e.getMessage()); }
                            return 1;
                        })))
                .then(Commands.literal("create")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                if (plugin.ui() != null) {
                                    plugin.ui().openCreate(player);
                                } else {
                                    help(plugin, player);
                                }
                            }
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player == null) {
                                        return 0;
                                    }
                                    service.create(player, StringArgumentType.getString(ctx, "name"),
                                            feedback(plugin, player));
                                    return 1;
                                })))
                .then(Commands.literal("found").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        doConfirm(plugin, player, "found");
                    }
                    return 1;
                }))
                .then(Commands.literal("accept").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        service.acceptInvite(player, feedback(plugin, player));
                    }
                    return 1;
                }))
                .then(Commands.literal("reject").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        service.rejectInvite(player, feedback(plugin, player));
                    }
                    return 1;
                }))
                .then(Commands.literal("invite")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                if (plugin.ui() != null) {
                                    plugin.ui().openInvitePicker(player, 1);
                                } else {
                                    help(plugin, player);
                                }
                            }
                            return 1;
                        })
                        .then(nameArg(plugin, "invite", "invite")))
                .then(Commands.literal("kick").then(nameArg(plugin, "kick", "kick")))
                .then(Commands.literal("transfer").then(nameArg(plugin, "transfer", "transfer")))
                .then(Commands.literal("promote").then(nameArg(plugin, "promote", "promote")))
                .then(Commands.literal("demote").then(nameArg(plugin, "demote", "demote")))
                .then(Commands.literal("leave").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        doConfirm(plugin, player, "leave");
                    }
                    return 1;
                }))
                .then(Commands.literal("disband").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        doConfirm(plugin, player, "disband");
                    }
                    return 1;
                }))
                .then(Commands.literal("spawn").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        service.townSpawn(player, feedback(plugin, player));
                    }
                    return 1;
                }))
                .then(Commands.literal("claim").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        final ChunkKey key = ChunkKey.of(player.getLocation());
                        doConfirm(plugin, player, "claimpos " + key.world() + " " + key.x() + " " + key.z());
                    }
                    return 1;
                }))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        final ChunkKey key = ChunkKey.of(player.getLocation());
                        doConfirm(plugin, player, "unclaimpos " + key.world() + " " + key.x() + " " + key.z());
                    }
                    return 1;
                }))
                .then(Commands.literal("claimmap")
                        .then(Commands.argument("row", IntegerArgumentType.integer(0, 8))
                                .then(Commands.argument("col", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> {
                                            final Player player = needPlayer(plugin, ctx);
                                            if (player != null) {
                                                doConfirm(plugin, player, "claimmap "
                                                        + IntegerArgumentType.getInteger(ctx, "row") + " "
                                                        + IntegerArgumentType.getInteger(ctx, "col"));
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("center").executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null && plugin.ui() != null) {
                                plugin.ui().openClaim(player);
                            } else if (player != null) {
                                sendChatMap(plugin, player);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("move")
                                .then(Commands.argument("dx", IntegerArgumentType.integer(-64, 64))
                                        .then(Commands.argument("dz", IntegerArgumentType.integer(-64, 64))
                                                .executes(ctx -> {
                                                    final Player player = needPlayer(plugin, ctx);
                                                    if (player != null) {
                                                        if (plugin.ui() != null) {
                                                            plugin.ui().moveClaim(player,
                                                                    IntegerArgumentType.getInteger(ctx, "dx"),
                                                                    IntegerArgumentType.getInteger(ctx, "dz"));
                                                        } else {
                                                            plugin.messages().send(player, "arcmenu-missing");
                                                        }
                                                    }
                                                    return 1;
                                                })))))
                .then(Commands.literal("unclaimat")
                        .then(Commands.argument("row", IntegerArgumentType.integer(0, 8))
                                .then(Commands.argument("col", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> {
                                            final Player player = needPlayer(plugin, ctx);
                                            if (player != null) {
                                                doConfirm(plugin, player, "unclaimat "
                                                        + IntegerArgumentType.getInteger(ctx, "row") + " "
                                                        + IntegerArgumentType.getInteger(ctx, "col"));
                                            }
                                            return 1;
                                        }))))
                .then(Commands.literal("members")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                openMembers(plugin, player, 1);
                            }
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        openMembers(plugin, player, IntegerArgumentType.getInteger(ctx, "page"));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("member")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        if (plugin.ui() != null) {
                                            plugin.ui().openMember(player, StringArgumentType.getString(ctx, "name"));
                                        } else {
                                            plugin.messages().send(player, "arcmenu-missing");
                                        }
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("invitepicker")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                openInvitePicker(plugin, player, 1);
                            }
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        openInvitePicker(plugin, player, IntegerArgumentType.getInteger(ctx, "page"));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("buff")
                        .then(Commands.literal("buy")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            final Player player = needPlayer(plugin, ctx);
                                            if (player != null) {
                                                doConfirm(plugin, player, "buff "
                                                        + StringArgumentType.getString(ctx, "id"));
                                            }
                                            return 1;
                                        }))))
                .then(Commands.literal("buffs")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                openBuffs(plugin, player, 1);
                            }
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        openBuffs(plugin, player, IntegerArgumentType.getInteger(ctx, "page"));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        doConfirm(plugin, player,
                                                StringArgumentType.getString(ctx, "action").trim());
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("confirm-cancel").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        clearConfirm(player);
                        if (plugin.ui() != null) {
                            plugin.ui().cancelConfirm(player);
                        } else {
                            plugin.messages().send(player, "confirm-cancelled");
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("ui-input")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        final String kind = StringArgumentType.getString(ctx, "kind");
                                        if ("name".equals(kind) || "invite".equals(kind)) {
                                            if (plugin.ui() != null) {
                                                plugin.ui().requestInput(player, kind);
                                            } else {
                                                plugin.messages().send(player, "input-unavailable");
                                            }
                                        }
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("ui-closed")
                        .then(Commands.argument("menu", StringArgumentType.word())
                                .executes(ctx -> {
                                    final Player player = player(ctx);
                                    if (player != null && plugin.ui() != null) {
                                        plugin.ui().closed(player, StringArgumentType.getString(ctx, "menu"));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                final TownSnapshotView view = service.townOf(player.getUniqueId());
                                if (view == null) {
                                    plugin.messages().send(player, "not-in-town");
                                } else {
                                    sendInfo(plugin, player, view);
                                }
                            }
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    final Player player = needPlayer(plugin, ctx);
                                    if (player != null) {
                                        final String name = StringArgumentType.getString(ctx, "name");
                                        TownSnapshotView found = null;
                                        for (final TownSnapshotView candidate : service.listTowns()) {
                                            if (candidate.name().equalsIgnoreCase(name)) {
                                                found = candidate;
                                                break;
                                            }
                                        }
                                        if (found == null) {
                                            plugin.messages().send(player, "town-not-found");
                                        } else {
                                            sendInfo(plugin, player, found);
                                        }
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("list").executes(ctx -> {
                    sendList(plugin, ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("map").executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player != null) {
                        sendChatMap(plugin, player);
                    }
                    return 1;
                }))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("town.reload"))
                        .executes(ctx -> {
                            plugin.reloadEverything();
                            ctx.getSource().getSender().sendMessage(plugin.messages().render("reloaded"));
                            return 1;
                        }))
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission("town.admin"))
                        .then(Commands.literal("reload").executes(ctx -> {
                            plugin.reloadEverything();
                            ctx.getSource().getSender().sendMessage(plugin.messages().render("reloaded"));
                            return 1;
                        }))
                        .then(Commands.literal("givehall").executes(ctx -> {
                            final Player player = needPlayer(plugin, ctx);
                            if (player != null) {
                                plugin.townHalls().give(player);
                                player.sendMessage(plugin.messages().render("hall-given"));
                            }
                            return 1;
                        })))
                .build();
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            nameArg(final TownPlugin plugin, final String literal, final String op) {
        return Commands.argument(literal, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (final Player online : plugin.getServer().getOnlinePlayers()) {
                        builder.suggest(online.getName());
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    final Player player = needPlayer(plugin, ctx);
                    if (player == null) {
                        return 0;
                    }
                    final String target = StringArgumentType.getString(ctx, literal);
                    switch (op) {
                        case "invite" -> plugin.service().invite(player, target, feedback(plugin, player));
                        case "kick", "transfer" -> doConfirm(plugin, player, op + " " + target);
                        case "promote" -> plugin.service().promote(player, target, feedback(plugin, player));
                        case "demote" -> plugin.service().demote(player, target, feedback(plugin, player));
                        default -> {
                        }
                    }
                    return 1;
                });
    }

    private static void openMembers(final TownPlugin plugin, final Player player, final int page) {
        if (plugin.ui() != null) {
            plugin.ui().openMembers(player, page);
        } else {
            sendMembers(plugin, player, page);
        }
    }

    private static void openInvitePicker(final TownPlugin plugin, final Player player, final int page) {
        if (plugin.ui() != null) {
            plugin.ui().openInvitePicker(player, page);
        } else {
            plugin.messages().send(player, "arcmenu-missing");
        }
    }

    private static void openBuffs(final TownPlugin plugin, final Player player, final int page) {
        if (plugin.ui() != null) {
            plugin.ui().openBuffs(player, page);
        } else {
            sendBuffs(plugin, player);
        }
    }

    private static Player player(final CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player player ? player : null;
    }

    private static Player needPlayer(final TownPlugin plugin, final CommandContext<CommandSourceStack> ctx) {
        final Player player = player(ctx);
        if (player == null) {
            ctx.getSource().getSender().sendMessage(plugin.messages().render("players-only"));
        }
        return player;
    }

    private static ChunkKey cellAt(final TownPlugin plugin, final Player player,
                                   final int row, final int col) {
        if (plugin.ui() != null) {
            return plugin.ui().cellAt(player, row, col);
        }
        final ChunkKey center = ChunkKey.of(player.getLocation());
        return new ChunkKey(center.world(), center.x() + col - 4, center.z() + row - 4);
    }

    private static Consumer<TownService.Result> feedback(final TownPlugin plugin, final Player player) {
        return result -> {
            plugin.service().deliver(player, result);
            if (plugin.ui() != null) {
                plugin.ui().afterResult(player, result);
            }
        };
    }

    // ------------------------------------------------------------------ 二次确认

    private static final Map<UUID, PendingConfirm> PENDING = new ConcurrentHashMap<>();
    private static final long CONFIRM_WINDOW_MS = 60_000L;

    private record PendingConfirm(String action, long expireAt) {
    }

    public static void clearConfirm(final Player player) {
        PENDING.remove(player.getUniqueId());
    }

    private static void doConfirm(final TownPlugin plugin, final Player player, final String action) {
        if (!validAction(action)) {
            plugin.messages().sendRaw(player, "<red>未知或格式错误的确认操作：" + action);
            return;
        }
        final PendingConfirm pending = PENDING.get(player.getUniqueId());
        final boolean armed = pending != null && pending.action().equals(action)
                && pending.expireAt() > System.currentTimeMillis();
        if (!armed) {
            PENDING.put(player.getUniqueId(), new PendingConfirm(action,
                    System.currentTimeMillis() + CONFIRM_WINDOW_MS));
            if (plugin.ui() != null) {
                plugin.ui().openConfirm(player, action);
            } else {
                plugin.messages().send(player, "confirm-armed",
                        Placeholder.unparsed("action", confirmLabel(action)));
            }
            return;
        }
        PENDING.remove(player.getUniqueId());
        executeConfirmed(plugin, player, action);
    }

    private static void executeConfirmed(final TownPlugin plugin, final Player player, final String action) {
        final TownService service = plugin.service();
        final Consumer<TownService.Result> fb = feedback(plugin, player);
        final String[] parts = action.split(" ");
        switch (parts[0]) {
            case "found" -> service.found(player, fb);
            case "leave" -> service.leave(player, fb);
            case "disband" -> service.disband(player, fb);
            case "kick" -> service.kick(player, parts[1], fb);
            case "transfer" -> service.transfer(player, parts[1], fb);
            case "buff" -> service.buyBuff(player, parts[1], fb);
            case "claimmap" -> service.claim(player,
                    cellAt(plugin, player, Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), fb);
            case "unclaimat" -> service.unclaim(player,
                    cellAt(plugin, player, Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), fb);
            case "claimpos", "unclaimpos" -> {
                final ChunkKey key = new ChunkKey(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if ("claimpos".equals(parts[0])) {
                    service.claim(player, key, fb);
                } else {
                    service.unclaim(player, key, fb);
                }
            }
            default -> plugin.messages().sendRaw(player, "<red>未知的确认操作：" + parts[0]);
        }
    }

    private static boolean validAction(final String action) {
        final String[] parts = action.split(" ");
        try {
            return switch (parts[0]) {
                case "found", "leave", "disband" -> parts.length == 1;
                case "kick", "transfer", "buff" -> parts.length == 2 && !parts[1].isBlank();
                case "claimmap", "unclaimat" -> parts.length == 3
                        && Integer.parseInt(parts[1]) >= 0 && Integer.parseInt(parts[1]) <= 8
                        && Integer.parseInt(parts[2]) >= 0 && Integer.parseInt(parts[2]) <= 8;
                case "claimpos", "unclaimpos" -> {
                    Integer.parseInt(parts[2]);
                    Integer.parseInt(parts[3]);
                    yield parts.length == 4 && !parts[1].isBlank();
                }
                default -> false;
            };
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static String confirmLabel(final String action) {
        return switch (action.split(" ")[0]) {
            case "found" -> "正式成立小镇";
            case "leave" -> "退出小镇";
            case "disband" -> "解散小镇";
            case "kick" -> "踢出成员";
            case "transfer" -> "转让镇长";
            case "buff" -> "购买领地祝福";
            case "claimmap", "claimpos" -> "购买区块";
            case "unclaimat", "unclaimpos" -> "放弃区块";
            default -> action;
        };
    }

    // ------------------------------------------------------------------ 文本降级

    private static void help(final TownPlugin plugin, final org.bukkit.command.CommandSender to) {
        final List<String> lines = List.of(
                "<gold>/town</gold> <gray>打开小镇界面",
                "<gold>/town create <名称></gold> <gray>筹备新小镇",
                "<gold>/town found</gold> <gray>成立小镇（需确认费用）",
                "<gold>/town invite <玩家></gold> <gray>邀请成员",
                "<gold>/town accept | reject</gold> <gray>接受 / 拒绝邀请",
                "<gold>/town kick | promote | demote | transfer <玩家></gold> <gray>成员管理",
                "<gold>/town leave | disband</gold> <gray>退出 / 解散（需确认）",
                "<gold>/town claim | unclaim</gold> <gray>圈下 / 放弃脚下区块（需确认）",
                "<gold>/town map</gold> <gray>文本地图",
                "<gold>/town spawn</gold> <gray>回小镇传送点",
                "<gold>/town info [名称] | list | members [页] | buffs</gold> <gray>信息查询",
                "<gold>/town buff buy <id></gold> <gray>购买领地祝福（需确认）",
                "<gold>/town confirm-cancel</gold> <gray>取消待确认操作");
        for (final String line : lines) {
            to.sendMessage(plugin.messages().renderPlain(line));
        }
    }

    private static void sendInfo(final TownPlugin plugin, final Player player, final TownSnapshotView view) {
        final String mayorName = org.bukkit.Bukkit.getOfflinePlayer(view.mayor()).getName();
        player.sendMessage(plugin.messages().renderPlain("<gold>──── <white>" + view.name() + "</white> ────"));
        player.sendMessage(plugin.messages().renderPlain("<gray>状态：<white>" + (view.active() ? "已成立" : "筹备中")));
        player.sendMessage(plugin.messages().renderPlain("<gray>镇长：<white>" + (mayorName == null ? "?" : mayorName)));
        player.sendMessage(plugin.messages().renderPlain("<gray>成员：<white>" + view.memberCount()));
        player.sendMessage(plugin.messages().renderPlain("<gray>领地：<white>" + view.claimCount() + " 块"));
        player.sendMessage(plugin.messages().renderPlain("<gray>祝福：<white>" + view.buffs().size() + " 个生效中"));
        player.sendMessage(plugin.messages().renderPlain("<gray>成立于：<white>" + view.createdDate()));
    }

    private static void sendMembers(final TownPlugin plugin, final Player player, final int requested) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        if (view == null) {
            plugin.messages().send(player, "not-in-town");
            return;
        }
        final var members = view.raw().membersByRole();
        final int pages = Math.max(1, (members.size() + 7) / 8);
        final int page = Math.min(Math.max(1, requested), pages);
        player.sendMessage(plugin.messages().renderPlain("<gold>── " + view.name()
                + " 成员（" + page + "/" + pages + "）──"));
        for (int i = (page - 1) * 8; i < Math.min(members.size(), page * 8); i++) {
            final var entry = members.get(i);
            final String name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
            player.sendMessage(plugin.messages().renderPlain("<gray>- <white>" + (name == null ? entry.getKey() : name)
                    + "</white> <dark_gray>·</dark_gray> <gray>" + entry.getValue().name()));
        }
    }

    private static void sendList(final TownPlugin plugin, final org.bukkit.command.CommandSender to) {
        final var towns = plugin.service().listTowns();
        if (towns.isEmpty()) {
            to.sendMessage(Component.text("服务器还没有小镇。", NamedTextColor.GRAY));
            return;
        }
        to.sendMessage(plugin.messages().renderPlain("<gold>── 小镇列表（" + towns.size() + "）──"));
        for (final var view : towns) {
            final String mayorName = org.bukkit.Bukkit.getOfflinePlayer(view.mayor()).getName();
            to.sendMessage(plugin.messages().renderPlain("<gray>- <white>" + view.name()
                    + "</white> <dark_gray>·</dark_gray> <gray>镇长 <white>"
                    + (mayorName == null ? "?" : mayorName)
                    + "</white> <dark_gray>·</dark_gray> <gray>" + view.memberCount() + " 人 / "
                    + view.claimCount() + " 块" + (view.active() ? "" : " <yellow>(筹备中)")));
        }
    }

    private static void sendBuffs(final TownPlugin plugin, final Player player) {
        final var view = plugin.service().townOf(player.getUniqueId());
        player.sendMessage(plugin.messages().renderPlain("<gold>── 领地祝福 ──"));
        final long now = System.currentTimeMillis();
        for (final var def : plugin.config().buffs()) {
            final Long expiry = view == null ? null : view.buffs().get(def.id());
            final String state;
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

    private static void sendChatMap(final TownPlugin plugin, final Player player) {
        final ChunkKey center = ChunkKey.of(player.getLocation());
        final var view = plugin.service().townAt(center);
        player.sendMessage(plugin.messages().renderPlain("<gold>── 领地地图 <gray>("
                + center.x() + ", " + center.z() + ")"));
        for (int dz = -5; dz <= 5; dz++) {
            final StringBuilder row = new StringBuilder();
            for (int dx = -5; dx <= 5; dx++) {
                final ChunkKey key = new ChunkKey(center.world(), center.x() + dx, center.z() + dz);
                final var owner = plugin.service().townAt(key);
                final String cell;
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

    private static String trim(final double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }
}
