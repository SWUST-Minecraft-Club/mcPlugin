package com.example.wallet;

import static org.junit.jupiter.api.Assertions.*;

import com.example.wallet.api.WalletAsset;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WalletConfigTest {
    private YamlConfiguration defaults() throws Exception {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private YamlConfiguration legacy() throws Exception {
        final var config = new YamlConfiguration();
        config.loadFromString("""
                base-url: https://wallet.example.invalid
                api-key: placeholder
                assets: []
                timezone: Asia/Shanghai
                """);
        config.setDefaults(defaults());
        return config;
    }

    @Test void legacyConfigWithPluginDefaultsRetainsTheDefaultVaultCurrency() throws Exception {
        final var config = legacy();
        final var loaded = WalletConfig.load(config);
        assertEquals(List.of(new WalletAsset("coin", "金币", "金币", 2, true, true, true)), loaded.currencies());
        assertEquals("local", loaded.backend());
        assertEquals("coin", loaded.vaultCurrency());
        assertTrue(loaded.vaultEnabled());
        assertEquals(loaded, WalletConfig.load(config));
        assertFalse(config.contains("currencies", true), "Loading must not create a section in the legacy config");
    }

    @Test void legacyConfigWithoutPluginDefaultsStillHasTheSameCurrency() throws Exception {
        final var config = legacy();
        config.setDefaults(new YamlConfiguration());
        assertEquals(List.of(new WalletAsset("coin", "金币", "金币", 2, true, true, true)),
                WalletConfig.load(config).currencies());
    }

    @Test void freshInstallLoadsThePackagedCoinDefinition() throws Exception {
        final var config = defaults();
        config.setDefaults(defaults());
        assertEquals(List.of(new WalletAsset("coin", "金币", "金币", 2, true, true, true)),
                WalletConfig.load(config).currencies());
    }

    @Test void explicitCustomCurrencyDoesNotGainAnUnconfiguredCoin() throws Exception {
        final var config = legacy();
        config.loadFromString("""
                currencies:
                  gold:
                    name: 金币
                    symbol: 金
                    scale: 0
                vault:
                  currency: gold
                """);
        final var loaded = WalletConfig.load(config);
        assertEquals(List.of(new WalletAsset("gold", "金币", "金", 0, true, true, true)), loaded.currencies());
        assertEquals("gold", loaded.vaultCurrency());
    }

    @Test void explicitDisabledCurrencyStaysDisabled() throws Exception {
        final var config = legacy();
        config.set("currencies.coin.enabled", false);
        final var loaded = WalletConfig.load(config);
        assertEquals(1, loaded.currencies().size());
        assertEquals("coin", loaded.currencies().getFirst().code());
        assertFalse(loaded.currencies().getFirst().enabled());
    }

    @Test void explicitlyEmptyCurrenciesAreNotReplacedWithCoin() throws Exception {
        final var config = legacy();
        config.createSection("currencies");
        config.set("vault.enabled", false);
        assertTrue(WalletConfig.load(config).currencies().isEmpty());
    }

    @Test void explicitWebsiteModePreservesEndpointAndSelectedAssets() throws Exception {
        final var config = legacy();
        config.set("backend", "website");
        config.set("assets", List.of("nether_star", "gold", "diamond", "emerald"));
        final var loaded = WalletConfig.load(config);
        assertEquals("website", loaded.backend());
        assertEquals("https://wallet.example.invalid", loaded.baseUrl());
        assertEquals("placeholder", loaded.apiKey());
        assertEquals(Set.of("nether_star", "gold", "diamond", "emerald"), loaded.enabledAssets());
    }
}
