package com.example.town;

import com.example.town.arcui.ArcUiManager;
import com.example.town.buff.BuffApplier;
import com.example.town.command.TownCommand;
import com.example.town.event.TownEvents;
import com.example.town.hook.CraftEngineHook;
import com.example.town.hook.MainMenuHook;
import com.example.town.hook.WalletHook;
import com.example.town.model.Town;
import com.example.town.protection.ProtectionListener;
import com.example.town.service.ChatInputs;
import com.example.town.service.TownService;
import com.example.town.store.TownStore;
import com.example.town.townhall.TownHallListener;
import com.example.town.townhall.TownHalls;
import com.example.town.util.Threads;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Town 小镇系统主类：成立 / 邀请 / 圈地 / 领地保护 / 领地 buff。
 * 界面走 ArcMenu（未安装时全部功能由 /town 指令等价提供）；
 * 经济走 Wallet；镇政厅道具支持 CraftEngine 自定义物品（缺失回落原版）。
 */
public final class TownPlugin extends JavaPlugin {

    private volatile TownConfig config;
    private TownRegistry registry;
    private TownStore store;
    private Messages messages;
    private TownEvents events;
    private WalletHook wallet;
    private CraftEngineHook craftEngine;
    private TownHalls townHalls;
    private TownService service;
    private ChatInputs chatInputs;
    private BuffApplier buffApplier;
    private ArcUiManager arcUi;
    private MainMenuHook menuHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = TownConfig.load(getConfig(), getLogger());
        messages = new Messages(this);

        registry = new TownRegistry();
        store = new TownStore(this);
        for (Town town : store.load()) {
            registry.register(town);
        }

        events = new TownEvents();
        wallet = WalletHook.create(this);
        craftEngine = CraftEngineHook.create(this);
        townHalls = new TownHalls(this, craftEngine);
        service = new TownService(this, registry, store, messages, events, wallet);
        com.example.town.api.TownApi.install(service);
        chatInputs = new ChatInputs(this);
        buffApplier = new BuffApplier(this, service);

        getServer().getPluginManager().registerEvents(chatInputs, this);
        getServer().getPluginManager().registerEvents(
                new TownHallListener(this, service, townHalls), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, service), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(TownCommand.create(this),
                        "小镇系统", List.of()));

        arcUi = ArcUiManager.create(this);
        if (arcUi != null) {
            arcUi.register();
        }
        menuHook = MainMenuHook.create(this);
        if (menuHook != null) {
            menuHook.register();
        }

        buffApplier.start();
        // 维护任务：邀请过期 / 筹备超时 / 过期 buff 惰性清理（30 秒一次）
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> service.maintenance(), 600L, 600L);

        getComponentLogger().info("Town 已启用：小镇 " + registry.all().size()
                + " 个；界面=" + (arcUi != null ? "ArcMenu" : "指令")
                + "，经济=" + (wallet != null ? "Wallet" : "未接入")
                + "，镇政厅=" + (craftEngine != null ? "CraftEngine" : "原版"));
    }

    @Override
    public void onDisable() {
        if (menuHook != null) {
            menuHook.unregister();
        }
        if (arcUi != null) {
            arcUi.unregister();
        }
        if (service != null) {
            service.saveNow();
        }
        com.example.town.api.TownApi.uninstall();
    }

    /** 重载配置（/town admin reload）：配置快照整体替换，菜单入口重挂。 */
    public void reloadEverything() {
        reloadConfig();
        config = TownConfig.load(getConfig(), getLogger());
        if (menuHook != null) {
            menuHook.unregister();
            menuHook.register();
        }
    }

    // ------------------------------------------------------------------
    // 访问器（供组件互相引用）
    // ------------------------------------------------------------------

    public TownConfig config() {
        return config;
    }

    public TownRegistry registry() {
        return registry;
    }

    public Messages messages() {
        return messages;
    }

    public TownService service() {
        return service;
    }

    public TownHalls townHalls() {
        return townHalls;
    }

    public ChatInputs chatInputs() {
        return chatInputs;
    }

    /** ArcMenu 界面管理器；未安装 ArcMenu 时为 null。 */
    public ArcUiManager arcUi() {
        return arcUi;
    }

    /** 给玩家打开小镇界面；ArcMenu 缺失时降级为提示。 */
    public void openUi(Player player) {
        if (arcUi != null) {
            arcUi.open(player);
        } else {
            player.sendMessage(messages.render("arcmenu-missing"));
        }
    }
}
