package com.example.wallet.local;

import com.example.wallet.api.*;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Reads the entire legacy snapshot before committing any local funds. */
public final class SiteMigration {
    public record Candidate(UUID id, String name) { }
    public record Result(int players, int skipped, int mismatched, LocalLedger.Migration migration) { }
    private SiteMigration() { }

    public static Result execute(final LocalLedger ledger, final List<WalletAsset> configured,
            final WalletService website, final List<Candidate> candidates, final String source) {
        return execute(ledger, configured, website, candidates, source, warning -> { });
    }

    public static Result execute(final LocalLedger ledger, final List<WalletAsset> configured,
            final WalletService website, final List<Candidate> candidates, final String source,
            final Consumer<String> onSkip) {
        ledger.beginImport();
        try {
            final List<WalletAsset> sourceAssets = website.assets().join();
            final List<WalletAsset> assets = configured.stream().filter(WalletAsset::enabled).toList();
            if (assets.isEmpty()) throw new WalletException(0, "没有配置可迁入币种");
            for (final WalletAsset target : assets) {
                final WalletAsset old = sourceAssets.stream().filter(a -> a.code().equalsIgnoreCase(target.code()))
                        .findFirst().orElseThrow(() -> new WalletException(0, "官网不存在币种：" + target.code()));
                if (old.scale() != target.scale()) throw new WalletException(0, "新旧币种小数精度不一致：" + target.code());
            }
            final List<LocalLedger.Imported> rows = new ArrayList<>();
            final var seen = new HashSet<UUID>();
            final var owners = new HashMap<String, UUID>();
            int skipped = 0, mismatched = 0, players = 0;
            for (final Candidate candidate : candidates) {
                if (!seen.add(candidate.id())) continue;
                final List<LocalLedger.Imported> own = new ArrayList<>();
                boolean skip = false;
                for (final WalletAsset target : assets) {
                    final WalletAsset old = sourceAssets.stream().filter(a -> a.code().equalsIgnoreCase(target.code())).findFirst().orElseThrow();
                    final WalletBalance balance;
                    try {
                        balance = website.balance(candidate.id(), candidate.name(), old.code()).join();
                    } catch (RuntimeException error) {
                        if (unbound(error)) {
                            onSkip.accept("迁入跳过玩家 " + candidate.name() + "（本服 UUID=" + candidate.id()
                                    + "，币种=" + old.code() + "）：玩家未绑定网站用户；该玩家全部币种均未迁入。");
                            skip = true;
                            break;
                        }
                        throw error;
                    }
                    if (balance.userId() == null || balance.userId().isBlank() || balance.balance() == null
                            || balance.balance().signum() < 0 || !balance.assetCode().equalsIgnoreCase(old.code())) {
                        throw new WalletException(0, "官网返回了不完整或无效余额：" + candidate.id());
                    }
                    if (balance.playerUuid() != null && !balance.playerUuid().isBlank()
                            && !candidate.id().toString().equalsIgnoreCase(balance.playerUuid())) {
                        onSkip.accept("迁入跳过玩家 " + candidate.name() + "（本服 UUID=" + candidate.id()
                                + "）：官网返回的游戏 UUID 与待迁入账户不一致；官网玩家=" + balance.playerName()
                                + "，官网 UUID=" + balance.playerUuid() + "，官网 userId=" + balance.userId()
                                + "，币种=" + old.code() + "；该玩家全部币种均未迁入，请另行核对补款。");
                        mismatched++;
                        skip = true;
                        break;
                    }
                    own.add(new LocalLedger.Imported(candidate.id(), candidate.name(), target.code(),
                            balance.balance().setScale(target.scale(), RoundingMode.UNNECESSARY), balance.userId()));
                }
                if (skip) { skipped++; continue; }
                // Only accepted players may reserve website identities for this batch.
                for (final LocalLedger.Imported row : own) {
                    final String identity = row.siteUser() + "|" + row.code();
                    final UUID previous = owners.putIfAbsent(identity, candidate.id());
                    if (previous != null && !previous.equals(candidate.id())) {
                        throw new WalletException(0, "同一官网账户映射到多个游戏 UUID，已停止迁入：" + row.siteUser());
                    }
                }
                rows.addAll(own); players++;
            }
            return new Result(players, skipped, mismatched, ledger.importOnce(source, rows));
        } finally {
            ledger.endImport();
        }
    }

    private static boolean unbound(Throwable error) {
        while (error != null) {
            if (error instanceof WalletException && error.getMessage() != null
                    && error.getMessage().contains("玩家未绑定网站用户")) return true;
            error = error.getCause();
        }
        return false;
    }
}
