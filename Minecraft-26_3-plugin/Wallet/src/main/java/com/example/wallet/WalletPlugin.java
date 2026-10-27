package com.example.wallet;

import com.example.wallet.api.WalletApi;
import com.example.wallet.command.WalletAdminCommand;
import com.example.wallet.command.WalletCommand;
import com.example.wallet.internal.RoutedWalletService;
import com.example.wallet.menu.WalletMenus;
import com.example.wallet.menu.WalletGuiListener;
import com.example.wallet.local.LocalWalletService;
import com.example.wallet.local.WalletVaultBridge;
import com.example.wallet.menu.WalletMainMenuHook;
import com.example.wallet.internal.AssetSwitchStore;
import com.example.wallet.internal.PlaytimeLedgerReader;
import com.example.wallet.internal.TestWalletServiceImpl;
import com.example.wallet.internal.WalletServiceImpl;
import com.example.wallet.api.WalletService;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Wallet：主菜单里的钱包（货币种类 / 余额 / 流水），同时对外提供 {@link WalletApi} 给后续插件依赖。
 *
 * <p>默认使用本地多币种账本并向 Vault 提供主币种；支持按币种混合官网来源及旧余额一次性迁入。
 */
public final class WalletPlugin extends JavaPlugin {

    private WalletConfig config;
    private WalletService service;
    private WalletMainMenuHook mainMenuHook;
    private AssetSwitchStore assetSwitches;
    private LocalWalletService localService;
    private WalletServiceImpl websiteService;
    private WalletMenus menus;

    public AssetSwitchStore assetSwitches() { return assetSwitches; }
    public WalletMenus walletMenus() { return menus; }
    public String walletSource(String code) { return config.testMode() ? "test" : config.source(code); }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try { this.config = WalletConfig.load(getConfig()); }
        catch (RuntimeException error) {
            getLogger().severe("钱包配置无效，停止插件：" + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.assetSwitches = new AssetSwitchStore(this, config.enabledAssets());
        if (this.config.testMode()) {
            getLogger().warning("============================================================");
            getLogger().warning("Wallet 本地测试模式已开启（test-mode.enabled=true）！");
            getLogger().warning("余额与流水只存本机（test-ledger.yml），不会写入官网。");
            getLogger().warning("上线前务必把 config.yml 的 test-mode.enabled 改回 false！");
            getLogger().warning("============================================================");
            this.service = new TestWalletServiceImpl(this, this.config);
        } else if (!this.config.backend().equals("website")) {
            try {
                this.localService = new LocalWalletService(this, config);
                this.service = localService;
                if (config.backend().equals("hybrid")) {
                    this.websiteService = new WalletServiceImpl(this, config);
                    this.service = new RoutedWalletService(config, localService, websiteService);
                }
                WalletVaultBridge.register(this, localService, config);
            } catch (java.io.IOException | RuntimeException error) {
                getLogger().severe("本地钱包启动失败，停止插件：" + error.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            this.websiteService = new WalletServiceImpl(this, this.config);
            this.service = websiteService;
        }
        WalletApi.install(this.service);
        this.menus = new WalletMenus(this, config, service, new PlaytimeLedgerReader(this), assetSwitches);
        getServer().getPluginManager().registerEvents(new WalletGuiListener(menus), this);
        if (websiteService != null && config.websiteConfigured()) {
            service.assets().exceptionally(error -> {
                getLogger().warning("官网币种暂时无法读取；本地币种仍可使用，请检查官网地址、密钥和币种精度。");
                return List.of();
            });
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("walletin", "给玩家增加货币（管理员）", List.of(),
                    new WalletAdminCommand(this, this.service, true));
            commands.register("walletout", "从玩家扣除货币（管理员）", List.of(),
                    new WalletAdminCommand(this, this.service, false));
            commands.register("wallet", "钱包余额、流水与管理员操作", List.of(),
                    new WalletCommand(this, service, config, localService, menus));
        });

        this.mainMenuHook = WalletMainMenuHook.create(this, this.config, this.service);
        if (this.mainMenuHook != null) {
            this.mainMenuHook.register();
        } else {
            getLogger().warning("MainMenu 未启用：可用 /wallet 打开钱包菜单，WalletApi 仍可供其它插件使用。");
        }

        if (!this.config.testMode() && (config.backend().equals("website") || config.backend().equals("hybrid"))
                && !this.config.websiteConfigured()) {
            getLogger().warning("钱包未配置：请在 config.yml 填 base-url 与 api-key"
                    + "（API Key 需要 plugin:yudream-wallet:manage 权限）。");
        }
    }

    @Override
    public void onDisable() {
        if (this.mainMenuHook != null) {
            this.mainMenuHook.unregister();
        }

        WalletApi.uninstall();
        getServer().getServicesManager().unregisterAll(this);
        if (this.websiteService != null) websiteService.shutdown();
        if (this.service instanceof TestWalletServiceImpl test) {
            test.shutdown();
        }
        if (this.localService != null) localService.close();
    }
}
