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
 * 管理员调整玩家货币：/wallet add|take|set，兼容 /walletin、/walletout。
 *
 * <p>操作走 WalletService 的 credit / debit / setBalance，
 * 对应后端流水即结果；businessNo 使用 {@code wallet-admin-add/take/set-<uuid>}，
 * 官网会把来源识别为 ADMIN。</p>
 */
public final class WalletAdminCommand implements BasicCommand {
    public enum Operation { ADD, TAKE, SET }
    private record Target(UUID id, String name) { }

    private static final String PERMISSION = "wallet.adjust";

    private final WalletPlugin plugin;
    private final WalletService service;
    private final Operation operation;
    private final String commandName;

    public WalletAdminCommand(WalletPlugin plugin, WalletService service, boolean credit) {
        this(plugin, service, credit ? Operation.ADD : Operation.TAKE, credit ? "walletin" : "walletout");
    }

    public WalletAdminCommand(WalletPlugin plugin, WalletService service, Operation operation, String commandName) {
        this.plugin = plugin;
        this.service = service;
        this.operation = operation;
        this.commandName = commandName;
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

        Target target = target(args[0]);
        if (target == null) {
            send(sender, "本服没有缓存该玩家，请使用准确 UUID 或在线玩家名：" + args[0], NamedTextColor.RED);
            return;
        }

        String code = args[1].trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = parseAmount(args[2], operation == Operation.SET);
        if (amount == null) {
            send(sender, operation == Operation.SET ? "目标余额必须是非负普通数字（允许 0）。"
                    : "数量必须是大于 0 的普通数字（不支持科学计数法）。", NamedTextColor.RED);
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
            String businessNo = "wallet-admin-" + operation.name().toLowerCase(Locale.ROOT) + "-"
                    + UUID.randomUUID().toString().replace("-", "");
            String remark = "管理员" + operation.name() + "：" + sender.getName();
            CompletableFuture<WalletTransaction> future;
            try {
                future = switch (operation) {
                    case ADD -> service.credit(target.id(), target.name(), asset.code(), normalized, businessNo, remark);
                    case TAKE -> service.debit(target.id(), target.name(), asset.code(), normalized, businessNo, remark);
                    case SET -> service.setBalance(target.id(), target.name(), asset.code(), normalized, businessNo, remark);
                };
            } catch (RuntimeException ex) {
                send(sender, "操作失败：" + message(ex), NamedTextColor.RED);
                return;
            }

            future.whenComplete((transaction, actionError) -> {
                if (actionError != null) {
                    send(sender, "操作失败：" + message(actionError), NamedTextColor.RED);
                    return;
                }
                String verb = switch (operation) { case ADD -> "增加"; case TAKE -> "扣除"; case SET -> "设置余额为"; };
                send(sender, "已为 " + target.name() + " " + verb + " "
                                + Format.amount(normalized, asset) + "（" + asset.name() + "），当前余额 "
                                + Format.amount(transaction.balanceAfter(), asset),
                        NamedTextColor.GREEN);
            });
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) {
            return filter(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[0]);
        }
        if (args.length == 2) {
            return filter(service.cachedAssets().stream()
                    .filter(WalletAsset::enabled)
                    .filter(a -> operation != Operation.SET || !plugin.walletSource(a.code()).equals("website"))
                    .map(WalletAsset::code)
                    .toList(), args[1]);
        }
        if (args.length == 3) return filter(operation == Operation.SET ? List.of("0", "100", "1000")
                : List.of("100", "1000"), args[2]);
        return List.of();
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    private Target target(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return new Target(player.getUniqueId(), player.getName());
            }
        }
        final var cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null) return new Target(cached.getUniqueId(), cached.getName());
        try {
            final UUID id = UUID.fromString(name);
            return new Target(id, name);
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static BigDecimal parseAmount(String raw, boolean allowZero) {
        if (raw == null || raw.isBlank() || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return value.signum() > 0 || (allowZero && value.signum() == 0) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static List<String> filter(Collection<String> values, String prefix) {
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT)
                .startsWith(prefix.toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
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
        send(sender, "用法：/" + commandName + " <玩家名或UUID> <币种编码> <金额>",
                NamedTextColor.YELLOW);
    }
}
