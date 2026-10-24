package com.example.wallet;

import com.example.wallet.api.WalletApi;
import com.example.wallet.command.WalletAdminCommand;
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
 * <p>货币与流水全部来自 YuDream 官网；所有加减货币都走官网接口（官网流水即上报结果），本地不存余额。
 */
public final class WalletPlugin extends JavaPlugin {

    private WalletConfig config;
    private WalletService service;
    private WalletMainMenuHook mainMenuHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = WalletConfig.load(getConfig());
        if (this.config.testMode()) {
            getLogger().warning("============================================================");
            getLogger().warning("Wallet 本地测试模式已开启（test-mode.enabled=true）！");
            getLogger().warning("余额与流水只存本机（test-ledger.yml），不会写入官网。");
            getLogger().warning("上线前务必把 config.yml 的 test-mode.enabled 改回 false！");
            getLogger().warning("============================================================");
            this.service = new TestWalletServiceImpl(this, this.config);
        } else {
            this.service = new WalletServiceImpl(this, this.config);
        }
        WalletApi.install(this.service);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("walletin", "给在线玩家增加货币（管理员）", List.of(),
                    new WalletAdminCommand(this, this.service, true));
            commands.register("walletout", "从在线玩家扣除货币（管理员）", List.of(),
                    new WalletAdminCommand(this, this.service, false));
        });

        this.mainMenuHook = WalletMainMenuHook.create(this, this.config, this.service);
        if (this.mainMenuHook != null) {
            this.mainMenuHook.register();
        } else {
            getLogger().warning("MainMenu 未启用：钱包菜单功能未注册，但 WalletApi 仍可被其它插件使用。");
        }

        if (!this.config.testMode() && !this.config.isConfigured()) {
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
        if (this.service instanceof WalletServiceImpl real) {
            real.shutdown();
        } else if (this.service instanceof TestWalletServiceImpl test) {
            test.shutdown();
        }
    }
}
