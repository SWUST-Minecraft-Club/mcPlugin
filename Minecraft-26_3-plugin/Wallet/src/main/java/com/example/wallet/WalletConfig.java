package com.example.wallet;

import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        ZoneId zone
) {

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
                zone
        );
    }

    public boolean isConfigured() {
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
