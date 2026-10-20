package com.example.wallet;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;
import com.example.wallet.api.WalletApi;
import com.example.wallet.internal.AssetSwitchStore;
import com.example.wallet.internal.PlaytimeLedgerReader;
import com.example.wallet.internal.WalletServiceImpl;
import com.example.wallet.menu.WalletFeature;
import com.example.wallet.menu.WalletGuiListener;
import com.example.wallet.menu.WalletMenus;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wallet：主菜单里的钱包（货币种类 / 余额 / 流水），同时对外提供 {@link WalletApi} 给后续插件依赖。
 *
 * <p>货币与流水全部来自 YuDream 官网；所有加减货币都走官网接口（官网流水即上报结果），本地不存余额。
 */
public final class WalletPlugin extends JavaPlugin {

    private WalletConfig config;
    private WalletServiceImpl service;
    private WalletMenus menus;
    private WalletFeature feature;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = WalletConfig.load(getConfig());
        this.service = new WalletServiceImpl(this, this.config);
        WalletApi.install(this.service);

        this.menus = new WalletMenus(this, this.config, this.service, new PlaytimeLedgerReader(this),
                new AssetSwitchStore(this, this.config.enabledAssets()));
        getServer().getPluginManager().registerEvents(new WalletGuiListener(this.menus), this);
        this.feature = new WalletFeature(this, this.config, this.service, this.menus);

        if (MainMenuApi.isAvailable()) {
            MainMenuApi.registry().register(this.feature);
            getLogger().info("已注册主菜单功能：钱包（绿宝石图标）。");
        } else {
            getLogger().warning("MainMenu 未启用：钱包菜单功能未注册，但 WalletApi 仍可被其它插件使用。");
        }

        if (!this.config.isConfigured()) {
            getLogger().warning("钱包未配置：请在 config.yml 填 base-url 与 api-key"
                    + "（API Key 需要 plugin:yudream-wallet:manage 权限）。");
        }
    }

    @Override
    public void onDisable() {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry != null) {
            try {
                registry.unregister("wallet:wallet");
            } catch (RuntimeException ignored) {
                // MainMenu 可能已经在关闭，忽略
            }
        }

        WalletApi.uninstall();
        if (this.service != null) {
            this.service.shutdown();
        }
    }
}
