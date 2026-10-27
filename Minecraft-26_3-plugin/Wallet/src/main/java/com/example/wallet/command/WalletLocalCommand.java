package com.example.wallet.command;

import com.example.wallet.WalletConfig;
import com.example.wallet.WalletPlugin;
import com.example.wallet.internal.PlaytimeLedgerReader;
import com.example.wallet.internal.WalletServiceImpl;
import com.example.wallet.local.LocalWalletService;
import com.example.wallet.local.SiteMigration;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WalletLocalCommand implements BasicCommand {
    private final WalletPlugin plugin;
    private final WalletConfig config;
    private final LocalWalletService local;
    private final AtomicBoolean importing = new AtomicBoolean();

    public WalletLocalCommand(WalletPlugin plugin, WalletConfig config, LocalWalletService local) {
        this.plugin = plugin; this.config = config; this.local = local;
    }

    @Override public void execute(final CommandSourceStack source, final String[] args) {
        final CommandSender sender = source.getSender();
        if (args.length == 0 || args[0].equals("balance")) {
            if (!(sender instanceof Player player)) { reply(sender, "用法：wallet migration-status 或 wallet migrate-site"); return; }
            for (final var asset : local.cachedAssets()) {
                if (asset.enabled()) reply(sender, asset.name() + "："
                        + local.ledger().balance(player.getUniqueId(), asset.code()).toPlainString() + " " + asset.symbol());
            }
            return;
        }
        if (!sender.hasPermission("wallet.migrate")) { reply(sender, "没有钱包迁入权限。"); return; }
        if (local.ledger().needsRecovery()) {
            reply(sender, "账本提交状态待核对；请检查磁盘并重启恢复后，再查看迁入状态。当前禁止再次迁入。");
            return;
        }
        if (args[0].equals("migration-status")) {
            final var state = local.ledger().migration();
            reply(sender, state.complete() ? "旧官网迁入已完成；" + state.rows() + " 条余额，完成时间 "
                    + java.time.Instant.ofEpochMilli(state.at()) + "。禁止重复执行。" : "尚未执行旧官网余额迁入。");
            return;
        }
        if (!args[0].equals("migrate-site")) { reply(sender, "用法：/wallet balance|migrate-site|migration-status"); return; }
        if (local.ledger().migration().complete()) { reply(sender, "旧官网余额已迁入，禁止重复执行。"); return; }
        if (!config.websiteConfigured()) { reply(sender, "迁入前请保留旧官网 base-url 与 api-key 配置，并重启服务端。"); return; }
        if (!importing.compareAndSet(false, true)) { reply(sender, "迁入正在进行中。"); return; }
        reply(sender, "正在读取已知玩家的官网余额；未绑定或 UUID 不一致的玩家将跳过并记录，其余账户校验通过后统一入账。");
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            final var candidates = new LinkedHashMap<UUID, SiteMigration.Candidate>();
            for (final var player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) candidates.put(player.getUniqueId(), new SiteMigration.Candidate(player.getUniqueId(), player.getName()));
            }
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try (var website = new WebsiteSource(plugin, config)) {
                    for (final var entry : new PlaytimeLedgerReader(plugin).read()) {
                        candidates.putIfAbsent(entry.playerId(), new SiteMigration.Candidate(entry.playerId(), entry.name()));
                    }
                    final var result = SiteMigration.execute(local.ledger(), local.cachedAssets(), website.service,
                            List.copyOf(candidates.values()), config.baseUrl(), plugin.getLogger()::warning);
                    final String skips = "跳过 " + result.skipped() + " 名（未绑定 "
                            + (result.skipped() - result.mismatched()) + "，UUID 不一致 " + result.mismatched() + "）";
                    plugin.getLogger().info("旧官网迁入已持久提交：" + result.players() + " 名玩家、"
                            + result.migration().rows() + " 条余额；" + skips + "。");
                    reply(sender, "迁入完成：" + result.players() + " 名玩家、" + result.migration().rows()
                            + " 条余额；" + skips + "。完成标记已持久保存，不能再次迁入。");
                    if (result.skipped() > 0) {
                        reply(sender, "跳过账户未入账，详情见服务端日志；这些账户需另行核对补款，不会自动补迁。");
                    }
                } catch (RuntimeException error) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    reply(sender, "迁入未完成：" + cause.getMessage() + "。请用 /wallet migration-status 核对结果。");
                } finally { importing.set(false); }
            });
        });
    }

    private void reply(final CommandSender sender, final String text) {
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, () -> player.sendMessage(Component.text("[钱包] " + text)), null, 1);
        } else sender.sendMessage(Component.text("[钱包] " + text));
    }

    private static final class WebsiteSource implements AutoCloseable {
        private final WalletServiceImpl service;
        WebsiteSource(WalletPlugin plugin, WalletConfig config) { service = new WalletServiceImpl(plugin, config); }
        @Override public void close() { service.shutdown(); }
    }
}
