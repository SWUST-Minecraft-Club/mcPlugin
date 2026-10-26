package com.example.mainmenu.economy;

import com.example.mainmenu.MainMenuPlugin;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Read-only account view. Vault and the selected provider own all balances and transfers. */
public final class VaultBalance {
    public record Snapshot(boolean available, String balance, String shortBalance,
                           String currency, String provider, String status) {
        public Component message() {
            return Component.text(available ? "余额：" + balance + " " + currency + "（" + provider + "）" : status);
        }
    }

    private final MainMenuPlugin plugin;
    private volatile Class<?> economyType;
    private ClassLoader vaultLoader;
    private Method enabled, getBalance, getName, plural, singular;

    public VaultBalance(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Snapshot read(final Player player) {
        final var vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return missing("未安装或启用 Vault", "");
        }
        try {
            final ClassLoader loader = vault.getClass().getClassLoader();
            if (economyType == null || vaultLoader != loader) {
                initialize(loader);
            }
            final RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) economyType);
            if (registration == null || !registration.getPlugin().isEnabled()) {
                return missing("Vault 已启用，尚无经济插件提供余额服务", "");
            }
            final Object service = registration.getProvider();
            final String provider = String.valueOf(getName.invoke(service));
            if (!Boolean.TRUE.equals(enabled.invoke(service))) {
                return missing("经济服务暂不可用", provider);
            }
            final double amount = ((Number) getBalance.invoke(service, player)).doubleValue();
            if (!Double.isFinite(amount) || amount < 0) {
                return missing("经济插件返回了无效余额", provider);
            }
            String currency = (String) plural.invoke(service);
            if (currency == null || currency.isBlank()) {
                currency = (String) singular.invoke(service);
            }
            if (currency == null || currency.isBlank()) {
                currency = "金币";
            }
            return new Snapshot(true, format(amount), compact(amount), currency, provider, "经济服务已连接");
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException error) {
            return missing("余额读取失败，请检查 Vault 经济服务", "");
        }
    }

    private synchronized void initialize(final ClassLoader loader) throws ReflectiveOperationException {
        final Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
        enabled = type.getMethod("isEnabled");
        getBalance = type.getMethod("getBalance", OfflinePlayer.class);
        getName = type.getMethod("getName");
        plural = type.getMethod("currencyNamePlural");
        singular = type.getMethod("currencyNameSingular");
        vaultLoader = loader;
        economyType = type;
    }

    private static Snapshot missing(final String status, final String provider) {
        return new Snapshot(false, "--", "--", "", provider, status);
    }

    static String format(final double amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
    }

    static String compact(final double amount) {
        if (amount >= 100_000_000) {
            return BigDecimal.valueOf(amount).movePointLeft(8).setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " 亿";
        }
        if (amount >= 10_000) {
            return BigDecimal.valueOf(amount).movePointLeft(4).setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " 万";
        }
        return format(amount);
    }
}
