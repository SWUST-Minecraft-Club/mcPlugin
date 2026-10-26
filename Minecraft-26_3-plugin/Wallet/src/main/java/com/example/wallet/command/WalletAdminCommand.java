package com.example.wallet.command;

import com.example.wallet.WalletPlugin;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletService;
import com.example.wallet.api.WalletTransaction;
import com.example.wallet.util.Format;
import com.example.wallet.util.PlayerTasks;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 管理员手动调整在线玩家货币：/walletin、/walletout。
 *
 * <p>所有操作都走 {@link WalletService#credit} / {@link WalletService#debit}，
 * 当前后端流水即结果；businessNo 使用 {@code wallet-admin-in/out-<uuid>}，
 * 官网会把来源识别为 ADMIN。</p>
 */
public final class WalletAdminCommand implements BasicCommand {

    private static final String PERMISSION = "wallet.adjust";

    private final WalletPlugin plugin;
    private final WalletService service;
    private final boolean credit;
    private final String commandName;

    public WalletAdminCommand(WalletPlugin plugin, WalletService service, boolean credit) {
        this.plugin = plugin;
        this.service = service;
        this.credit = credit;
        this.commandName = credit ? "walletin" : "walletout";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "你没有权限执行该操作（需要 " + PERMISSION + "）。", NamedTextColor.RED);
            return;
        }
        if (args.length != 3) {
            usage(sender);
            return;
        }

        Player target = onlinePlayer(args[0]);
        if (target == null) {
            send(sender, "玩家不在线：" + args[0], NamedTextColor.RED);
            return;
        }

        String code = args[1].trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = parseAmount(args[2]);
        if (amount == null) {
            send(sender, "数量必须是大于 0 的普通数字（不支持科学计数法）。", NamedTextColor.RED);
            return;
        }

        CompletableFuture<List<WalletAsset>> assets = service.cachedAssets().isEmpty()
                ? service.assets()
                : CompletableFuture.completedFuture(service.cachedAssets());
        assets.whenComplete((list, error) -> {
            if (error != null) {
                send(sender, "读取货币失败：" + message(error), NamedTextColor.RED);
                return;
            }
            WalletAsset asset = find(list, code);
            if (asset == null) {
                send(sender, "钱包没有该币种：" + code, NamedTextColor.RED);
                return;
            }
            if (!asset.enabled()) {
                send(sender, "该币种已停用：" + code, NamedTextColor.RED);
                return;
            }
            if (amount.stripTrailingZeros().scale() > asset.scale()) {
                send(sender, asset.name() + " 最多允许 " + asset.scale() + " 位小数。",
                        NamedTextColor.RED);
                return;
            }

            BigDecimal normalized = amount.setScale(asset.scale());
            String businessNo = (credit ? "wallet-admin-in-" : "wallet-admin-out-")
                    + UUID.randomUUID().toString().replace("-", "");
            String remark = (credit ? "管理员加币：" : "管理员扣币：") + sender.getName();
            CompletableFuture<WalletTransaction> future;
            try {
                future = credit
                        ? service.credit(target.getUniqueId(), target.getName(), asset.code(),
                                normalized, businessNo, remark)
                        : service.debit(target.getUniqueId(), target.getName(), asset.code(),
                                normalized, businessNo, remark);
            } catch (RuntimeException ex) {
                send(sender, "操作失败：" + message(ex), NamedTextColor.RED);
                return;
            }

            future.whenComplete((transaction, actionError) -> {
                if (actionError != null) {
                    send(sender, "操作失败：" + message(actionError), NamedTextColor.RED);
                    return;
                }
                String verb = credit ? "增加" : "扣除";
                send(sender, "已为 " + target.getName() + " " + verb + " "
                                + Format.amount(normalized, asset) + "（" + asset.name() + "），当前余额 "
                                + Format.amount(transaction.balanceAfter(), asset),
                        NamedTextColor.GREEN);
            });
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 1) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
        }
        if (args.length == 2) {
            return service.cachedAssets().stream()
                    .filter(WalletAsset::enabled)
                    .map(WalletAsset::code)
                    .toList();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    private Player onlinePlayer(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private WalletAsset find(List<WalletAsset> assets, String code) {
        if (assets == null) {
            return null;
        }
        for (WalletAsset asset : assets) {
            if (asset != null && asset.code() != null && asset.code().equalsIgnoreCase(code)) {
                return asset;
            }
        }
        return null;
    }

    private String message(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException || cause instanceof ExecutionException) {
            Throwable next = cause.getCause();
            if (next == null || next == cause) {
                break;
            }
            cause = next;
        }
        if (cause instanceof WalletException && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return cause.getMessage() == null ? "Wallet 调用失败" : cause.getMessage();
    }

    private void send(CommandSender sender, String text, NamedTextColor color) {
        if (!plugin.isEnabled()) {
            return;
        }
        final Component message = Component.text("[钱包] ", NamedTextColor.GRAY)
                .append(Component.text(text, color));
        // 异步 HTTP 回调可能落在任意线程；玩家消息回到玩家所属区域线程发送
        if (sender instanceof Player player) {
            PlayerTasks.run(plugin, player, () -> player.sendMessage(message));
        } else {
            sender.sendMessage(message);
        }
    }

    private void usage(CommandSender sender) {
        send(sender, "用法：/" + commandName + " <玩家名> <币种编码> <数量>",
                NamedTextColor.YELLOW);
    }
}
