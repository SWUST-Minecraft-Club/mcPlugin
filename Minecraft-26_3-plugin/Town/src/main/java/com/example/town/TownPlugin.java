package com.example.town;

import com.example.town.arcmenu.TownUi;
import com.example.town.arcmenu.TownUiListener;
import com.example.town.buff.BuffApplier;
import com.example.town.command.TownCommand;
import com.example.town.event.TownEvents;
import com.example.town.hook.CraftEngineHook;
import com.example.town.hook.MainMenuHook;
import com.example.town.hook.WalletHook;
import com.example.town.model.Town;
import com.example.town.protection.ProtectionListener;
import com.example.town.service.TownService;
import com.example.town.service.PlotService;
import com.example.town.service.CivicService;
import com.example.town.selection.SelectionService;
import com.example.town.store.TownStore;
import com.example.town.townhall.TownHallListener;
import com.example.town.townhall.TownHalls;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Town 小镇系统主类：成立 / 邀请 / 圈地 / 领地保护 / 领地 buff。
 * 界面 = 由插件生成 YAML 交给 ArcMenu 渲染（arcmenu-designing 技能规范）；
 * 未装 ArcMenu 时全部功能由 /town 指令等价提供。经济走 Wallet；镇政厅道具支持 CraftEngine。
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
    private PlotService plots;
    private CivicService civic;
    private com.example.town.relocation.RelocationService relocation;
    private com.example.town.market.MarketService market;
    private SelectionService selections;
    private BuffApplier buffApplier;
    private TownUi ui;
    private MainMenuHook menuHook;

    @Override
    public void onLoad() {
        if(getServer().getPluginManager().getPlugin("CraftEngine")!=null) {
            try {com.example.town.hook.TownAssets.install(this);}
            catch(java.io.IOException error) {getLogger().warning("小镇 CraftEngine 资源释放失败："+error.getMessage());}
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // copyDefaults 之前迁移旧半径，防止新默认值覆盖运营者原先选择的赠地额度。
        if (!getConfig().isSet("founding.free-claim-count") && getConfig().isSet("founding.free-claim-radius")) {
            int radius=Math.clamp(getConfig().getInt("founding.free-claim-radius"),0,8);
            getConfig().set("founding.free-claim-count",(radius*2+1)*(radius*2+1));
        }
        // 旧安装的 config.yml 缺少新版本新增键（如 claim-already-yours）时补齐默认值，避免「缺少消息」
        getConfig().options().copyDefaults(true);
        saveConfig();
        config = TownConfig.load(getConfig(), getLogger());
        messages = new Messages(this);

        registry = new TownRegistry();
        store = new TownStore(this);
        for (Town town : store.load()) {
            try {
                registry.register(town);
            } catch (final IllegalArgumentException e) {
                throw new IllegalStateException("小镇 " + town.id() + " 索引冲突，停止加载以保留原数据",e);
            }
        }

        events = new TownEvents();
        wallet = WalletHook.create(this);
        craftEngine = CraftEngineHook.create(this);
        townHalls = new TownHalls(this, craftEngine);
        service = new TownService(this, registry, store, messages, events, wallet);
        plots = new PlotService(registry, service::requestSave, plotLimits());
        try { civic=new CivicService(this,store); }
        catch(java.io.IOException error) { throw new IllegalStateException("支付台账不可用，停止加载以保留待结算交易",error); }
        try {relocation=new com.example.town.relocation.RelocationService(this);}
        catch(java.io.IOException error) {throw new IllegalStateException("迁移日志不可用，停止加载以保护施工区域",error);}
        try {market=new com.example.town.market.MarketService(this);}
        catch(java.io.IOException error) {throw new IllegalStateException("商业台账不可用，停止加载以保护托管资产",error);}
        plots.commercialAssets(market::hasAssets);
        getServer().getPluginManager().registerEvents(new com.example.town.market.MarketListener(this),this);
        plots.constructionLock(relocation.locks()::intersects);
        getServer().getPluginManager().registerEvents(new com.example.town.relocation.ConstructionListener(this),this);
        selections = new SelectionService(this);
        getServer().getPluginManager().registerEvents(selections,this);
        selections.start();
        if (wallet != null) {
            wallet.preloadAssets();
        }
        com.example.town.api.TownApi.install(service);
        buffApplier = new BuffApplier(this, service);
        getServer().getPluginManager().registerEvents(buffApplier,this);
        getServer().getPluginManager().registerEvents(new com.example.town.buff.HarvestListener(this),this);

        getServer().getPluginManager().registerEvents(
                new TownHallListener(this, service, townHalls), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, service), this);
        if(craftEngine!=null) craftEngine.protect(this);
        getServer().getPluginManager().registerEvents(new TownUiListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(TownCommand.create(this),
                        "小镇系统", List.of()));

        ui = TownUi.create(this);
        if (ui != null) {
            ui.installMenus();
        }
        menuHook = MainMenuHook.create(this);
        if (menuHook != null) {
            menuHook.register();
        }

        buffApplier.start();
        relocation.start();
        market.start();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,task->civic.payments().recover(),100,100);
        // 维护任务：邀请过期 / 筹备超时 / 过期 buff 惰性清理（30 秒一次）
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> service.maintenance(), 600L, 600L);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> {
                    if (ui != null) {
                        ui.tick();
                    }
                }, 40L, 40L);

        getComponentLogger().info("Town 已启用：小镇 " + registry.all().size()
                + " 个；界面=" + (ui != null ? "ArcMenu" : "指令")
                + "，经济=" + (wallet != null ? "Wallet" : "未接入")
                + "，镇政厅=" + (craftEngine != null ? "CraftEngine" : "原版"));
    }

    @Override
    public void onDisable() {
        if (menuHook != null) {
            menuHook.unregister();
        }
        if (service != null) {
            service.saveNow();
        }
        if (buffApplier != null) {
            buffApplier.stop();
        }
        if (ui != null) {
            ui.shutdown();
        }
        com.example.town.api.TownApi.uninstall();
    }

    /** 重载配置（/town admin reload）：配置快照整体替换，菜单重新生成并让 ArcMenu 重载。 */
    public void reloadEverything() {
        reloadConfig();
        config = TownConfig.load(getConfig(), getLogger());
        plots.limits(plotLimits());
        civic.reload();
        if (wallet != null) {
            wallet.preloadAssets();
        }
        if (buffApplier != null) {
            buffApplier.restart();
        }
        if (menuHook != null) {
            menuHook.unregister();
            menuHook.register();
        }
        if (ui != null) {
            ui.installMenus();
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

    public PlotService plots() { return plots; }
    public CivicService civic() { return civic; }
    public com.example.town.relocation.RelocationService relocation() {return relocation;}
    public com.example.town.market.MarketService market() {return market;}
    public CraftEngineHook craftEngine() {return craftEngine;}
    public TownStore store() { return store; }
    public TownEvents events() { return events; }
    public SelectionService selections() { return selections; }

    private PlotService.Limits plotLimits() {
        return new PlotService.Limits(
                Math.clamp(getConfig().getInt("plots.max-area",65536),1,4_194_304),
                Math.clamp(getConfig().getInt("plots.max-per-town",256),1,65536),
                Math.clamp(getConfig().getLong("plots.proposal-seconds",120),10,3600)*1000);
    }

    public TownHalls townHalls() {
        return townHalls;
    }

    /** ArcMenu 界面粘合层；未安装 ArcMenu 时为 null。 */
    public TownUi ui() {
        return ui;
    }

    /** 给玩家打开小镇主页；ArcMenu 缺失时降级为提示。 */
    public void openUi(Player player) {
        if (ui != null) {
            ui.openHome(player);
        } else {
            player.sendMessage(messages.render("arcmenu-missing"));
        }
    }
}
