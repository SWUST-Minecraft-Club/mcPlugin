package com.example.town.arcui;

import com.example.town.TownPlugin;
import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuApplicationHandle;
import com.fentai.arcmenu.api.ArcMenuApplicationOptions;
import com.fentai.arcmenu.api.ArcMenuCapability;
import com.fentai.arcmenu.api.ArcMenuCanvas;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * ArcMenu 软依赖入口：com.fentai 类型允许出现在本包（arcui 即钩子），
 * 但主类只允许引用本类。create() 返回 null 表示 ArcMenu 不可用（调用方降级到指令通道）。
 */
public final class ArcUiManager {

    /** 界面应用 id。 */
    public static final String APP_ID = "town:ui";

    private final TownPlugin plugin;
    private final ArcMenuApi api;
    private ArcMenuApplicationHandle handle;

    private ArcUiManager(TownPlugin plugin, ArcMenuApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    /** ArcMenu 存在且支持 Application 会话时创建；否则返回 null。 */
    public static ArcUiManager create(TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("ArcMenu") == null) {
            return null;
        }
        try {
            ArcMenuApi api = plugin.getServer().getServicesManager().load(ArcMenuApi.class);
            if (api == null) {
                plugin.getLogger().warning("ArcMenu 已安装但 API 未注册，图形界面不可用。");
                return null;
            }
            if (!api.capabilities().contains(ArcMenuCapability.APPLICATION_SESSIONS)) {
                plugin.getLogger().warning("ArcMenu 不支持 Application 会话（版本过旧？），图形界面不可用。");
                return null;
            }
            plugin.getLogger().info("已接入 ArcMenu（apiVersion=" + api.apiVersion() + "）。");
            return new ArcUiManager(plugin, api);
        } catch (LinkageError e) {
            plugin.getLogger().warning("ArcMenu API 类不可用，图形界面禁用："
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 注册界面应用（onEnable 调用）。 */
    public void register() {
        ArcMenuApplicationOptions options = ArcMenuApplicationOptions.builder()
                .permission("town.use")
                .inheritCanvas(false)
                .canvas(new ArcMenuCanvas(UiMetrics.CANVAS_W, UiMetrics.CANVAS_H,
                        UiMetrics.PXB, UiMetrics.DESIGN_DISTANCE))
                .tickInterval(20)
                .build();
        handle = api.registerApplication(plugin, APP_ID, options, new TownUiApplication(plugin));
    }

    /** 给玩家打开小镇界面。 */
    public void open(Player player) {
        api.openApplication(player, APP_ID, List.of());
    }

    /**
     * 菜单内文本输入（ArcMenu 3.2+ 原生聊天捕获，无需关闭界面）。
     * 回调参数：ok=true 时 text 为提交内容；false 表示取消 / 关闭 / 不可用。
     */
    public void requestText(Player player, String title, int maxLength,
                            java.util.function.BiConsumer<Boolean, String> callback) {
        var options = com.fentai.arcmenu.api.ArcMenuTextInputOptions.builder()
                .title(title)
                .maxLength(maxLength)
                .build();
        api.textInput().request(plugin, player, options,
                new com.fentai.arcmenu.api.ArcMenuTextInputListener() {
                    @Override
                    public void onComplete(com.fentai.arcmenu.api.ArcMenuTextInputResult result) {
                        boolean committed = result.getReason()
                                == com.fentai.arcmenu.api.ArcMenuTextInputEndReason.SUBMITTED;
                        callback.accept(committed, result.getValue());
                    }
                });
    }

    /** onDisable 注销。 */
    public void unregister() {
        if (handle != null && handle.isRegistered()) {
            handle.unregister();
        }
    }
}
