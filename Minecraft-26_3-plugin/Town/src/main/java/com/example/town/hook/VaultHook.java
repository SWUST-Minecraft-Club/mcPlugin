package com.example.town.hook;

import com.example.town.TownPlugin;
import com.example.town.economy.Payment;
import com.example.town.economy.VaultLedger;
import com.example.town.economy.VaultMoney;
import com.example.town.util.Threads;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** 标准 Vault Economy 适配；反射隔离可选 API，不将 Vault 类打进 Town。 */
public final class VaultHook implements EconomyHook {
    private record Connection(Object service, Plugin owner, String id, String name, String symbol, int digits) { }
    private record Cached(double balance, long at) { }
    private final TownPlugin plugin;
    private final Class<?> economyType;
    private final VaultLedger ledger;
    private final Method enabled, providerName, fractionalDigits, plural, singular;
    private final Method getBalance, hasAccount, createAccount, withdraw, deposit, success;
    private final Field responseAmount, responseBalance, responseError;
    private final ConcurrentMap<UUID, Cached> balances = new ConcurrentHashMap<>();
    private volatile Connection bound;

    private VaultHook(TownPlugin plugin, ClassLoader loader) throws ReflectiveOperationException, IOException {
        this.plugin = plugin;
        economyType = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
        Class<?> response = Class.forName("net.milkbowl.vault.economy.EconomyResponse", true, loader);
        enabled = economyType.getMethod("isEnabled");
        providerName = economyType.getMethod("getName");
        fractionalDigits = economyType.getMethod("fractionalDigits");
        plural = economyType.getMethod("currencyNamePlural");
        singular = economyType.getMethod("currencyNameSingular");
        getBalance = economyType.getMethod("getBalance", OfflinePlayer.class);
        hasAccount = economyType.getMethod("hasAccount", OfflinePlayer.class);
        createAccount = economyType.getMethod("createPlayerAccount", OfflinePlayer.class);
        withdraw = economyType.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        deposit = economyType.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        success = response.getMethod("transactionSuccess");
        responseAmount = response.getField("amount");
        responseBalance = response.getField("balance");
        responseError = response.getField("errorMessage");
        ledger = new VaultLedger(plugin.getDataFolder().toPath().resolve("vault-transfers.bin"));
    }

    public static EconomyHook create(TownPlugin plugin) throws IOException {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            plugin.getLogger().warning("选择了 Vault 经济，但 Vault 未启用；收费功能暂不可用。");
            return null;
        }
        try {
            VaultHook hook = new VaultHook(plugin, vault.getClass().getClassLoader());
            Connection ready = hook.connection();
            plugin.getLogger().info(ready == null
                    ? "Vault 已接入，等待经济插件注册 Economy 服务。"
                    : "已接入 Vault 经济服务：" + ready.name());
            return hook;
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().warning("Vault Economy API 不兼容，经济功能暂不可用：" + error.getClass().getSimpleName());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RegisteredServiceProvider<?> registration() {
        return Bukkit.getServicesManager().getRegistration((Class) economyType);
    }

    /** 服务可以晚于 Town 注册；一旦绑定，在本次启动期间不自动切换账本。 */
    private synchronized Connection connection() {
        if (bound != null) return bound;
        RegisteredServiceProvider<?> registration = registration();
        if (registration == null || !registration.getPlugin().isEnabled()) return null;
        try {
            Object service = registration.getProvider();
            if (!Boolean.TRUE.equals(invoke(enabled, service))) return null;
            String name = String.valueOf(invoke(providerName, service));
            String symbol = (String) invoke(plural, service);
            if (symbol == null || symbol.isBlank()) symbol = (String) invoke(singular, service);
            if (symbol == null || symbol.isBlank()) symbol = name;
            bound = new Connection(service, registration.getPlugin(),
                    "vault:" + registration.getPlugin().getName() + ":" + name, name, symbol,
                    ((Number) invoke(fractionalDigits, service)).intValue());
            return bound;
        } catch (Exception error) { return null; }
    }

    private boolean available(Connection c) {
        RegisteredServiceProvider<?> current = registration();
        if (c == null || current == null || current.getProvider() != c.service() || !c.owner().isEnabled()) return false;
        try { return Boolean.TRUE.equals(invoke(enabled, c.service())); }
        catch (Exception error) { return false; }
    }

    public String id() { Connection c = connection(); return c == null ? "vault" : c.id(); }
    public String name() { Connection c = connection(); return c == null ? "Vault（等待经济服务）" : "Vault / " + c.name(); }
    public String resolveCurrency(String configured) { Connection c = connection(); return available(c) ? c.id() : ""; }
    public String symbolOf(String configured, String code) { Connection c = connection(); return c == null ? "" : c.symbol(); }
    public CompletableFuture<Void> preloadAssets() { connection(); return CompletableFuture.completedFuture(null); }
    public VaultLedger vaultLedger() { return ledger; }
    public boolean acceptsCurrency(String code) { return !code.isBlank() && code.equals(resolveCurrency("")); }
    public boolean definiteRejection(Throwable error) { return error instanceof VaultLedger.Rejected; }

    public Double cachedBalance(UUID player, String code) {
        if (!acceptsCurrency(code)) return null;
        Cached cached = balances.get(player);
        return cached != null && System.nanoTime() - cached.at() < TimeUnit.SECONDS.toNanos(2) ? cached.balance() : null;
    }

    public CompletableFuture<Double> balance(UUID player, String name, String code) {
        Connection c = connection();
        if (!available(c) || !c.id().equals(code)) return CompletableFuture.completedFuture(-1.0);
        return onAccount(player, () -> {
            if (!available(c)) throw new IllegalStateException("Vault 经济来源暂不可用");
            double value = ((Number) invoke(getBalance, c.service(), Bukkit.getOfflinePlayer(player))).doubleValue();
            if (!Double.isFinite(value) || value < 0) throw new IllegalStateException("Vault 返回无效余额");
            balances.put(player, new Cached(value, System.nanoTime()));
            return value;
        }).exceptionally(error -> -1.0);
    }

    public CompletableFuture<Void> debitCents(Payment p) {
        return transfer(p, p.debitKey(), p.payer(), p.cents(), false);
    }

    public CompletableFuture<Void> creditCents(Payment p, boolean refund) {
        return transfer(p, refund ? p.refundKey() : p.creditKey(), refund ? p.payer() : p.beneficiary(),
                refund ? p.cents() : p.creditCents(), true);
    }

    private CompletableFuture<Void> transfer(Payment p, String key, UUID account, long cents, boolean credit) {
        Connection c = connection();
        if (!available(c)) return CompletableFuture.failedFuture(new IllegalStateException("Vault 经济来源暂不可用"));
        final double amount;
        try {
            checkBinding(p);
            if (!c.id().equals(p.currency())) throw new VaultLedger.Rejected("交易币种属于其他经济来源");
            amount = VaultMoney.amount(cents, c.digits());
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        return ledger.execute(new VaultLedger.Entry(key, c.id(), account, cents, credit, VaultLedger.State.PENDING),
                () -> onAccount(account, () -> {
                    if (!available(c)) throw new VaultLedger.NotCalled("Vault 服务在调用前已停用");
                    OfflinePlayer player = Bukkit.getOfflinePlayer(account);
                    if (credit && !Boolean.TRUE.equals(invoke(hasAccount, c.service(), player))
                            && !Boolean.TRUE.equals(invoke(createAccount, c.service(), player))) {
                        throw new VaultLedger.Rejected("经济插件不能创建收款账户");
                    }
                    Object response;
                    try { response = invoke(credit ? deposit : withdraw, c.service(), player, amount); }
                    finally { balances.remove(account); }
                    if (response == null) throw new VaultLedger.Uncertain(key);
                    if (!Boolean.TRUE.equals(invoke(success, response))) {
                        // FAILURE 必须没有转移金额；不符合约定时不能当成安全拒绝。
                        if (((Number) responseAmount.get(response)).doubleValue() != 0.0) throw new VaultLedger.Uncertain(key);
                        throw new VaultLedger.Rejected(String.valueOf(responseError.get(response)));
                    }
                    if (!VaultMoney.matches(((Number) responseAmount.get(response)).doubleValue(), cents)) {
                        throw new VaultLedger.Uncertain(key);
                    }
                    double after = ((Number) responseBalance.get(response)).doubleValue();
                    if (Double.isFinite(after) && after >= 0) balances.put(account, new Cached(after, System.nanoTime()));
                    return (Void) null;
                }), task -> Threads.async(plugin, task));
    }

    @FunctionalInterface private interface AccountCall<T> { T call() throws Exception; }

    /** 在线账户归玩家实体线程，离线账户归全局线程；不在异步 IO 线程直接调用经济插件。 */
    private <T> CompletableFuture<T> onAccount(UUID id, AccountCall<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable action = () -> {
            try { result.complete(operation.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        };
        try {
            Player player = Bukkit.getPlayer(id);
            if (player == null) Bukkit.getGlobalRegionScheduler().execute(plugin, action);
            else if (Bukkit.isOwnedByCurrentRegion(player)) action.run();
            else if (!player.getScheduler().execute(plugin, action,
                    () -> result.completeExceptionally(new VaultLedger.NotCalled("玩家在调用前已离线")), 1L)) {
                result.completeExceptionally(new VaultLedger.NotCalled("玩家账户调度不可用"));
            }
        } catch (RuntimeException error) { result.completeExceptionally(new VaultLedger.NotCalled("账户调度失败")); }
        return result;
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw error;
        }
    }
}
