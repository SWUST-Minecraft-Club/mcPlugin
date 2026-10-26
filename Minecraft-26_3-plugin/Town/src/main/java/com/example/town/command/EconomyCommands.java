package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.economy.Money;
import com.example.town.economy.VaultLedger;
import com.example.town.util.Threads;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /town admin economy：Vault 结果不明时查看与人工对账，不直接修改玩家余额。 */
final class EconomyCommands {
    private EconomyCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> create(TownPlugin plugin) {
        return Commands.literal("economy")
                .requires(source -> source.getSender().hasPermission("town.admin"))
                .executes(ctx -> status(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("pending").executes(ctx -> pending(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("resolve")
                        .then(Commands.argument("payment", StringArgumentType.word())
                                .then(Commands.argument("leg", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String s : List.of("debit", "credit", "refund")) builder.suggest(s);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("result", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (String s : List.of("success", "failed", "retry")) builder.suggest(s);
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> resolve(plugin, ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "payment"),
                                                        StringArgumentType.getString(ctx, "leg"),
                                                        StringArgumentType.getString(ctx, "result")))))));
    }

    private static int status(TownPlugin plugin, CommandSender sender) {
        var hook = plugin.service().wallet();
        reply(plugin, sender, "当前经济来源：" + (hook == null ? "未接入" : hook.name())
                + "；币种：" + plugin.civic().currency());
        reply(plugin, sender, "Vault 核对：pending 查看；resolve <交易UUID> <debit|credit|refund> <success|failed|retry>。请先核对外部账本。");
        return 1;
    }

    private static VaultLedger ledger(TownPlugin plugin, CommandSender sender) {
        var hook = plugin.service().wallet();
        VaultLedger ledger = hook == null ? null : hook.vaultLedger();
        if (ledger == null) reply(plugin, sender, "当前未选择或未加载 Vault 经济适配器。");
        return ledger;
    }

    private static int pending(TownPlugin plugin, CommandSender sender) {
        VaultLedger ledger = ledger(plugin, sender);
        if (ledger == null) return 0;
        List<VaultLedger.Entry> entries = ledger.pending();
        reply(plugin, sender, "Vault 未确认 / 已拒绝记录：" + entries.size() + " 条（最多显示 50 条）。");
        entries.stream().limit(50).forEach(e -> reply(plugin, sender,
                e.key() + " · " + e.state() + " · " + e.account() + " · "
                        + (e.credit() ? "+" : "-") + Money.format(e.cents()) + " · " + e.provider()));
        return 1;
    }

    private static int resolve(TownPlugin plugin, CommandSender sender, String payment, String leg, String result) {
        VaultLedger ledger = ledger(plugin, sender);
        if (ledger == null) return 0;
        final String key;
        final VaultLedger.Resolution resolution;
        try {
            key = "town:" + UUID.fromString(payment) + ":" + leg;
            if (!List.of("debit", "credit", "refund").contains(leg)) throw new IllegalArgumentException();
            resolution = VaultLedger.Resolution.valueOf(result.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            reply(plugin, sender, "用法：/town admin economy resolve <交易UUID> <debit|credit|refund> <success|failed|retry>");
            return 0;
        }
        Threads.async(plugin, () -> {
            try {
                ledger.resolve(key, resolution);
                plugin.getLogger().warning("管理员 " + sender.getName() + " 核对 Vault 交易 " + key + "：" + resolution);
                plugin.civic().payments().recover();
                reply(plugin, sender, "已保存核对结果 " + resolution + "；交易将按原业务号继续处理。");
            } catch (Exception error) { reply(plugin, sender, "核对失败：" + error.getMessage()); }
        });
        return 1;
    }

    private static void reply(TownPlugin plugin, CommandSender sender, String text) {
        Runnable task = () -> sender.sendMessage(Component.text(text));
        if (sender instanceof Player player) Threads.forPlayer(plugin, player, task);
        else Threads.global(plugin, task);
    }
}
