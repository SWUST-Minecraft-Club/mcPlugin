package com.example.wallet.local;

import com.example.wallet.WalletConfig;
import com.example.wallet.WalletPlugin;
import com.example.wallet.api.WalletAsset;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

/** Uses Vault's actual API class loader; no copied Vault classes are packaged. */
public final class WalletVaultBridge implements InvocationHandler {
    private final WalletPlugin plugin;
    private final LocalWalletService service;
    private final WalletAsset asset;
    private final Constructor<?> response;
    private final Object success, failure, unsupported;

    private WalletVaultBridge(final WalletPlugin plugin, final LocalWalletService service,
                              final WalletAsset asset, final ClassLoader loader) throws ReflectiveOperationException {
        this.plugin = plugin; this.service = service; this.asset = asset;
        final Class<?> type = Class.forName("net.milkbowl.vault.economy.EconomyResponse", true, loader);
        final Class<?> result = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType", true, loader);
        response = type.getConstructor(double.class, double.class, result, String.class);
        success = result.getField("SUCCESS").get(null);
        failure = result.getField("FAILURE").get(null);
        unsupported = result.getField("NOT_IMPLEMENTED").get(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(final WalletPlugin plugin, final LocalWalletService service, final WalletConfig config) {
        if (!config.vaultEnabled()) return;
        final var vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            plugin.getLogger().warning("Vault 未启用；本地 WalletApi 可用，Vault 经济服务未注册。"); return;
        }
        final WalletAsset asset = service.asset(config.vaultCurrency());
        try {
            final ClassLoader loader = vault.getClass().getClassLoader();
            final Class type = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
            if (plugin.getServer().getServicesManager().getRegistration(type) != null) {
                throw new IllegalStateException("已有其他 Vault 经济服务；请先移除多余经济插件，避免使用两套余额");
            }
            final WalletVaultBridge bridge = new WalletVaultBridge(plugin, service, asset, loader);
            final Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {type}, bridge);
            plugin.getServer().getServicesManager().register(type, provider, plugin, ServicePriority.Normal);
            plugin.getLogger().info("Wallet 已向 Vault 注册主币种：" + asset.code() + "（" + asset.name() + "）");
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Vault API 不兼容，不能注册 Wallet 经济服务", error);
        }
    }

    @Override public Object invoke(final Object proxy, final Method method, final Object[] raw) throws Throwable {
        final Object[] args = raw == null ? new Object[0] : raw;
        return switch (method.getName()) {
            case "toString" -> "WalletVault[" + asset.code() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "isEnabled" -> plugin.isEnabled() && service.ledger().available()
                    && plugin.assetSwitches().isEnabled(asset.code(), service.cachedAssets());
            case "getName" -> "Wallet/" + asset.code();
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> asset.scale();
            case "currencyNameSingular", "currencyNamePlural" -> asset.name();
            case "format" -> BigDecimal.valueOf(((Number) args[0]).doubleValue())
                    .setScale(asset.scale(), RoundingMode.HALF_UP).toPlainString() + " " + asset.symbol();
            case "getBanks" -> List.of();
            case "createBank", "deleteBank", "bankBalance", "bankHas", "bankWithdraw", "bankDeposit",
                 "isBankOwner", "isBankMember" -> response.newInstance(0d, 0d, unsupported, "不支持 Vault 银行账户");
            case "hasAccount" -> account(args[0]) != null && service.ledger().hasAccount(account(args[0]).getUniqueId(), asset.code());
            case "getBalance" -> balance(account(args[0]));
            case "has" -> {
                final double amount = ((Number) args[args.length - 1]).doubleValue();
                yield Double.isFinite(amount) && amount >= 0 && account(args[0]) != null
                        && service.ledger().balance(account(args[0]).getUniqueId(), asset.code())
                        .compareTo(BigDecimal.valueOf(amount)) >= 0;
            }
            case "createPlayerAccount" -> {
                final OfflinePlayer player = account(args[0]);
                if (player == null || !enabled()) yield false;
                try { service.ledger().createAccount(player.getUniqueId(), player.getName(), asset.code()); yield true; }
                catch (RuntimeException error) { yield false; }
            }
            case "depositPlayer", "withdrawPlayer" -> transfer(args, method.getName().equals("depositPlayer"));
            default -> throw new UnsupportedOperationException("Unsupported Vault method: " + method.getName());
        };
    }

    private boolean enabled() {
        return plugin.isEnabled() && service.ledger().available()
                && plugin.assetSwitches().isEnabled(asset.code(), service.cachedAssets());
    }
    private OfflinePlayer account(final Object value) {
        if (value instanceof OfflinePlayer player) return player;
        return value instanceof String name ? plugin.getServer().getOfflinePlayerIfCached(name) : null;
    }
    private double balance(final OfflinePlayer player) {
        return player == null ? 0 : service.ledger().balance(player.getUniqueId(), asset.code()).doubleValue();
    }
    private Object transfer(final Object[] args, final boolean credit) throws ReflectiveOperationException {
        final OfflinePlayer player = account(args[0]);
        if (player == null || !enabled()) return response.newInstance(0d, balance(player), failure, "账户或经济服务不可用");
        final double amount = ((Number) args[args.length - 1]).doubleValue();
        if (!Double.isFinite(amount) || amount < 0) return response.newInstance(0d, balance(player), failure, "金额无效");
        try {
            if (amount == 0) return response.newInstance(0d, balance(player), success, "");
            final var tx = service.changeNow(player.getUniqueId(), player.getName(), asset.code(), BigDecimal.valueOf(amount),
                    credit, "vault:" + UUID.randomUUID(), "Vault " + (credit ? "入账" : "扣款"));
            return response.newInstance(tx.amount().doubleValue(), tx.balanceAfter().doubleValue(), success, "");
        } catch (RuntimeException error) {
            // A failed durable commit may have reached disk. Do not claim a safe zero-amount rejection.
            if (service.ledger().needsRecovery()) throw new IllegalStateException("账本提交状态待核对", error);
            return response.newInstance(0d, balance(player), failure, error.getMessage());
        }
    }
}
