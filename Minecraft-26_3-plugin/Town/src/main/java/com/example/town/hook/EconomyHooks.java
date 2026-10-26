package com.example.town.hook;

import com.example.town.TownPlugin;
import java.io.IOException;
import java.util.Locale;

public final class EconomyHooks {
    private EconomyHooks() { }

    public static String configured(TownPlugin plugin) {
        return plugin.getConfig().getString("economy.provider", "wallet").trim().toLowerCase(Locale.ROOT);
    }

    public static EconomyHook create(TownPlugin plugin) {
        return switch (configured(plugin)) {
            case "wallet" -> WalletHook.create(plugin);
            case "vault" -> {
                try { yield VaultHook.create(plugin); }
                catch (IOException error) {
                    throw new IllegalStateException("Vault 交易台账不可用，停止加载以保护待结算款项", error);
                }
            }
            default -> {
                plugin.getLogger().severe("economy.provider 只支持 wallet / vault，经济功能暂不可用。");
                yield null;
            }
        };
    }
}
