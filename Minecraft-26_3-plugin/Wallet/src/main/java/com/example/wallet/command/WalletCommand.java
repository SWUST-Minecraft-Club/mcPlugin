package com.example.wallet.command;

import com.example.wallet.WalletConfig;
import com.example.wallet.WalletPlugin;
import com.example.wallet.api.*;
import com.example.wallet.local.LocalWalletService;
import com.example.wallet.menu.WalletMenus;
import com.example.wallet.util.PlayerTasks;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** One command surface for local, website and explicitly routed wallets. */
public final class WalletCommand implements BasicCommand {
    private final WalletPlugin plugin;
    private final WalletService service;
    private final WalletConfig config;
    private final WalletLocalCommand migration;
    private final WalletMenus menus;
    private final Map<String, WalletAdminCommand> adjustments;

    public WalletCommand(WalletPlugin plugin, WalletService service, WalletConfig config, LocalWalletService local, WalletMenus menus) {
        this.plugin = plugin; this.service = service; this.config = config; this.menus = menus;
        this.migration = local == null ? null : new WalletLocalCommand(plugin, config, local);
        this.adjustments = Map.of("add", new WalletAdminCommand(plugin, service, WalletAdminCommand.Operation.ADD, "wallet add"),
                "take", new WalletAdminCommand(plugin, service, WalletAdminCommand.Operation.TAKE, "wallet take"),
                "set", new WalletAdminCommand(plugin, service, WalletAdminCommand.Operation.SET, "wallet set"));
    }

    @Override public void execute(CommandSourceStack source, String[] args) {
        final CommandSender sender = source.getSender();
        final String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (adjustments.containsKey(action)) {
            adjustments.get(action).execute(source, Arrays.copyOfRange(args, 1, args.length)); return;
        }
        if (action.equals("migrate-site") || action.equals("migration-status")) {
            if (migration == null) reply(sender, "当前后端没有可迁入的本地账本。");
            else migration.execute(source, args);
            return;
        }
        if (action.equals("help")) {
            reply(sender, "用法：/wallet balance [币种] | history [币种] [页码]");
            if (sender.hasPermission("wallet.admin")) reply(sender, "管理菜单：/wallet assets | players");
            if (sender.hasPermission("wallet.adjust")) reply(sender, "管理货币：/wallet add|take|set <玩家名或UUID> <币种> <金额>（set 仅本地）");
            if (sender.hasPermission("wallet.migrate") && migration != null) reply(sender, "迁入：/wallet migrate-site | migration-status");
            return;
        }
        if (!(sender instanceof Player player)) { reply(sender, "此操作需要玩家执行；控制台可用 /wallet help 查看管理命令。"); return; }
        switch (action) {
            case "assets", "players" -> {
                if (!sender.hasPermission("wallet.admin")) { reply(sender, "没有钱包管理权限。"); return; }
                if (action.equals("assets")) menus.openAssets(player); else menus.openPlayers(player, 1);
            }
            case "balance" -> {
                final UUID id = player.getUniqueId(); final String name = player.getName();
                final var available = service.cachedAssets().isEmpty() ? service.assets()
                        : java.util.concurrent.CompletableFuture.completedFuture(service.cachedAssets());
                available.whenComplete((list, loadingError) -> {
                    if (loadingError != null) { reply(sender, "读取货币失败：" + message(loadingError)); return; }
                    final List<WalletAsset> assets = list.stream().filter(WalletAsset::enabled)
                            .filter(a -> args.length < 2 || a.code().equalsIgnoreCase(args[1])).toList();
                    if (assets.isEmpty()) { reply(sender, "没有匹配的启用币种。"); return; }
                    for (var asset : assets) service.balance(id, name, asset.code()).whenComplete((balance, error) -> {
                        if (error != null) reply(sender, asset.name() + "：" + message(error));
                        else reply(sender, asset.name() + "（" + asset.code() + " / "
                                + (config.source(asset.code()).equals("website") ? "官网" : "本地") + "）：" + balance.balance().toPlainString());
                    });
                });
            }
            case "history" -> {
                final String code = args.length > 1 ? args[1] : null;
                if (code != null && menus.enabledCodes().stream().noneMatch(c -> c.equalsIgnoreCase(code))) {
                    reply(sender, "没有匹配的启用币种。"); return;
                }
                int page = 1;
                try { if (args.length > 2) page = Integer.parseInt(args[2]); }
                catch (NumberFormatException error) { reply(sender, "页码必须为正整数。"); return; }
                if (page < 1) { reply(sender, "页码必须为正整数。"); return; }
                menus.openHistory(player, page, code);
            }
            default -> reply(sender, "未知子命令，请用 /wallet help 查看用法。");
        }
    }

    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        final CommandSender sender = source.getSender();
        if (args.length <= 1) {
            final List<String> names = new ArrayList<>(List.of("help", "balance", "history"));
            if (sender.hasPermission("wallet.admin")) names.addAll(List.of("assets", "players"));
            if (sender.hasPermission("wallet.adjust")) names.addAll(List.of("set", "add", "take"));
            if (sender.hasPermission("wallet.migrate") && migration != null) names.addAll(List.of("migrate-site", "migration-status"));
            return WalletAdminCommand.filter(names, args.length == 0 ? "" : args[0]);
        }
        final String action = args[0].toLowerCase(Locale.ROOT);
        if (adjustments.containsKey(action)) return adjustments.get(action).suggest(source, Arrays.copyOfRange(args, 1, args.length));
        if ((action.equals("balance") || action.equals("history")) && args.length == 2) {
            return WalletAdminCommand.filter(service.cachedAssets().stream().filter(WalletAsset::enabled).map(WalletAsset::code).toList(), args[1]);
        }
        return List.of();
    }

    private void reply(CommandSender sender, String value) {
        if (!plugin.isEnabled()) return;
        final Component message = Component.text("[钱包] " + value);
        if (sender instanceof Player player) PlayerTasks.run(plugin, player, () -> player.sendMessage(message));
        else sender.sendMessage(message);
    }
    private static String message(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return String.valueOf(error.getMessage());
    }
}
