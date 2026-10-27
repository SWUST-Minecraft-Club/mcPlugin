package com.example.wallet;

import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import com.example.wallet.api.WalletAsset;
import org.bukkit.configuration.file.FileConfiguration;

/** Wallet 配置（config.yml）。 */
public record WalletConfig(
        String baseUrl,
        String apiKey,
        Set<String> enabledAssets,
        long assetsCacheSeconds,
        long balanceCacheSeconds,
        int timeoutMs,
        String matchMode,
        int historyPageSize,
        int adminPageSize,
        ZoneId zone,
        boolean testMode,
        String testCurrencyCode,
        String testCurrencyName,
        String testCurrencySymbol,
        long testStartBalance,
        String backend,
        List<WalletAsset> currencies,
        boolean vaultEnabled,
        String vaultCurrency,
        Map<String, CurrencyRoute> routes
) {
    public record CurrencyRoute(String source, String websiteCode) { }

    public List<WalletAsset> localCurrencies() {
        return currencies.stream().filter(a -> routes.get(a.code()).source().equals("local")).toList();
    }

    public String source(final String code) {
        if (backend.equals("website")) return "website";
        final CurrencyRoute route = routes.get(code.toLowerCase(Locale.ROOT));
        return route == null ? "unknown" : route.source();
    }

    public static WalletConfig load(final FileConfiguration config) {
        final String baseUrl = stripTrailingSlash(config.getString("base-url", ""));
        final Set<String> assets = new LinkedHashSet<>();
        final List<String> rawAssets = config.getStringList("assets");
        for (final String raw : rawAssets) {
            if (raw != null && !raw.trim().isEmpty()) {
                assets.add(raw.trim().toLowerCase());
            }
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(config.getString("timezone", "Asia/Shanghai"));
        } catch (RuntimeException e) {
            zone = ZoneId.of("Asia/Shanghai");
        }

        final String backend = config.getString("backend", "local").toLowerCase(Locale.ROOT);
        if (!Set.of("local", "website", "hybrid").contains(backend)) {
            throw new IllegalArgumentException("backend 必须是 local、website 或 hybrid");
        }
        final List<WalletAsset> currencies = new ArrayList<>();
        final Map<String, CurrencyRoute> routes = new LinkedHashMap<>();
        // getConfigurationSection 会为仅存在于 defaults 的节创建空节，getKeys(false) 却不含默认键。
        // 先检查文件中实际存在的节，让旧配置继续使用默认币种，保留显式空节与自定义币种。
        final var section = config.contains("currencies", true) ? config.getConfigurationSection("currencies") : null;
        if (section == null) {
            currencies.add(new WalletAsset("coin", "金币", "金币", 2, true, true, true));
            routes.put("coin", new CurrencyRoute(backend.equals("website") ? "website" : "local", "coin"));
        } else {
            for (final String code : section.getKeys(false)) {
                if (!code.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("无效币种编码：" + code);
                final int scale = section.getInt(code + ".scale", 2);
                if (scale < 0 || scale > 6) throw new IllegalArgumentException("币种小数位必须为 0～6");
                final String source = section.getString(code + ".source", backend.equals("website") ? "website" : "local")
                        .trim().toLowerCase(Locale.ROOT);
                if (!Set.of("local", "website").contains(source)) throw new IllegalArgumentException("币种 source 必须是 local 或 website：" + code);
                if (backend.equals("local") && source.equals("website")) throw new IllegalArgumentException("配置官网币种时请使用 backend: hybrid：" + code);
                final String websiteCode = section.getString(code + ".website-code", code).trim();
                if (!websiteCode.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("无效官网币种编码：" + websiteCode);
                routes.put(code.toLowerCase(Locale.ROOT), new CurrencyRoute(source, websiteCode));
                currencies.add(new WalletAsset(code.toLowerCase(Locale.ROOT), section.getString(code + ".name", code),
                        section.getString(code + ".symbol", code), scale, true,
                        section.getBoolean(code + ".enabled", true), section.getBoolean(code + ".transfer-enabled", true)));
            }
        }
        if (currencies.stream().map(WalletAsset::code).distinct().count() != currencies.size()) {
            throw new IllegalArgumentException("币种编码不区分大小写，不能重复");
        }
        if (routes.values().stream().filter(r -> r.source().equals("website"))
                .map(r -> r.websiteCode().toLowerCase(Locale.ROOT)).distinct().count()
                != routes.values().stream().filter(r -> r.source().equals("website")).count()) {
            throw new IllegalArgumentException("多个币种不能映射到同一官网币种");
        }
        final String vaultCurrency = config.getString("vault.currency", "coin").toLowerCase(Locale.ROOT);
        if (backend.equals("hybrid") && config.getBoolean("vault.enabled", true)
                && (!routes.containsKey(vaultCurrency) || !routes.get(vaultCurrency).source().equals("local"))) {
            throw new IllegalArgumentException("Vault 主币种必须选择本地币种：" + vaultCurrency);
        }
        return new WalletConfig(
                baseUrl,
                config.getString("api-key", "").trim(),
                assets,
                positive(config.getLong("assets-cache-seconds", 60L), 60L),
                positive(config.getLong("balance-cache-seconds", 30L), 30L),
                (int) positive(config.getLong("request-timeout-ms", 8000L), 8000L),
                config.getString("match-mode", "auto"),
                clamp(config.getInt("history-page-size", 45), 1, 45),
                clamp(config.getInt("admin-page-size", 45), 1, 45),
                zone,
                config.getBoolean("test-mode.enabled", false),
                config.getString("test-mode.currency-code", "TEST").trim().toUpperCase(),
                config.getString("test-mode.currency-name", "测试币"),
                config.getString("test-mode.currency-symbol", "✦"),
                Math.max(0, config.getLong("test-mode.start-balance", 100000L)),
                backend, List.copyOf(currencies), config.getBoolean("vault.enabled", true),
                vaultCurrency, Map.copyOf(routes)
        );
    }

    public boolean isConfigured() {
        return testMode || !backend.equals("website") || websiteConfigured();
    }

    public boolean websiteConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty();
    }

    /** 本服是否启用某种货币（assets 留空 = 官网启用的都算启用）。 */
    public boolean isLocallyEnabled(final String assetCode) {
        return assetCode != null && (enabledAssets.isEmpty() || enabledAssets.contains(assetCode.toLowerCase()));
    }

    private static String stripTrailingSlash(final String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static long positive(final long value, final long fallback) {
        return value > 0L ? value : fallback;
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
