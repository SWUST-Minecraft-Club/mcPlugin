package com.example.joinannounce;

import com.example.joinannounce.command.JoinAnnounceCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JoinAnnounce：玩家进服时发送可配置公告。
 *
 * <p>进服公告、首次进服文案、进出服广播是核心功能，不依赖其它插件；
 * 主菜单里的「服务器公告」入口是附加项，MainMenu 不存在时自动跳过。
 *
 * <p>注意：本类<b>不能</b>直接引用 MainMenu 的类，否则没装 MainMenu 时会在类校验阶段
 * 抛 {@code NoClassDefFoundError}、插件直接加载失败。MainMenu 相关代码见 {@link MenuHook}。
 */
public final class JoinAnnouncePlugin extends JavaPlugin {

    static final String MENU_FEATURE_ID = "joinannounce:notice";

    /** 配置快照在 reload 时整体替换，跨区域线程读取，必须 volatile */
    private volatile AnnouncementSettings settings;
    private volatile MenuHook menuHook;
    private volatile boolean menuRegistered;

    private final DailyNoticeGate dailyGate = new DailyNoticeGate();

    private AnnouncementService announcements;
    private AnnouncementDialog dialogs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = AnnouncementSettings.load(getConfig(), getComponentLogger());
        this.dialogs = new AnnouncementDialog();
        this.announcements = new AnnouncementService(this);

        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(JoinAnnounceCommand.create(this), "进服公告管理（reload / preview）"));

        applyMenuRegistration();

        getComponentLogger().info(Component.text(
                "已启用：进服公告"
                        + (this.settings.announceEnabled() ? "（延迟 " + this.settings.delayTicks() + " tick）" : "（进服公告已关闭）")
                        + (this.menuRegistered ? "；主菜单入口已注册" : "；主菜单入口未注册"),
                NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        final MenuHook hook = this.menuHook;
        if (hook != null) {
            try {
                hook.unregister();
            } catch (final LinkageError ignored) {
                // MainMenu 已经先卸载，类不再可用
            }
        }
        this.menuRegistered = false;
    }

    /** 重新读取 config.yml，并按需重算主菜单入口（排序值改动靠重新注册生效） */
    public void reloadSettings() {
        reloadConfig();
        this.settings = AnnouncementSettings.load(getConfig(), getComponentLogger());
        applyMenuRegistration();
    }

    public AnnouncementSettings settings() {
        return this.settings;
    }

    public AnnouncementService announcements() {
        return this.announcements;
    }

    AnnouncementDialog dialogs() {
        return this.dialogs;
    }

    DailyNoticeGate dailyGate() {
        return this.dailyGate;
    }

    /** 读取并渲染一条配置消息（用于命令回执） */
    public Component message(final String key, final String fallback) {
        final String raw = getConfig().getString(key, fallback);
        return this.announcements.render(raw == null ? fallback : raw, null);
    }

    private synchronized void applyMenuRegistration() {
        final boolean menuEnabled = this.settings.menu().enabled();
        try {
            if (this.menuHook == null) {
                // 用 Bukkit 的插件查询判断 MainMenu 是否存在，这里不碰任何 MainMenu 类型
                if (getServer().getPluginManager().getPlugin("MainMenu") == null) {
                    this.menuRegistered = false;
                    if (menuEnabled) {
                        getComponentLogger().warn(Component.text(
                                "MainMenu 未启用：跳过主菜单「服务器公告」入口（进服公告不受影响）",
                                NamedTextColor.YELLOW));
                    }
                    return;
                }
                this.menuHook = new MenuHook(this);
            }
            this.menuRegistered = this.menuHook.apply(menuEnabled);
        } catch (final LinkageError ex) {
            this.menuRegistered = false;
            getComponentLogger().warn(Component.text(
                    "MainMenu 的类不可用：" + ex.getMessage() + "（进服公告不受影响）", NamedTextColor.YELLOW));
            return;
        } catch (final RuntimeException ex) {
            this.menuRegistered = false;
            getComponentLogger().warn(Component.text(
                    "注册主菜单入口失败：" + ex.getMessage(), NamedTextColor.YELLOW));
            return;
        }

        if (menuEnabled && !this.menuRegistered) {
            getComponentLogger().warn(Component.text(
                    "MainMenu 尚未就绪：本次未注册「服务器公告」入口", NamedTextColor.YELLOW));
        }
    }
}
