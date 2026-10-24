package com.example.joinannounce;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;

/**
 * MainMenu 引用的隔离层。
 *
 * <p>JoinAnnounce 对 MainMenu 是软依赖：没装 MainMenu 时插件也要能正常加载。
 * 主类里只要出现 MainMenu 的类型（字段、局部变量、方法调用），JVM 在类校验阶段就会去加载它们，
 * 没装 MainMenu 时直接 {@link NoClassDefFoundError}。所以 MainMenu 相关代码全部收拢在这里，
 * 由主类确认 {@code getPlugin("MainMenu") != null} 之后才创建本类。
 */
final class MenuHook {

    private final JoinAnnouncePlugin plugin;
    private NoticeMenuFeature feature;
    private boolean registered;

    MenuHook(final JoinAnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 按配置同步主菜单入口：{@code enabled} 为 false 时摘掉入口。
     *
     * @return 是否已注册到主菜单
     */
    boolean apply(final boolean enabled) {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry == null) {
            this.registered = false;
            return false;
        }
        if (this.registered) {
            unregister();
        }
        if (!enabled) {
            return false;
        }
        if (this.feature == null) {
            this.feature = new NoticeMenuFeature(this.plugin);
        }
        registry.register(this.feature);
        this.registered = true;
        return true;
    }

    void unregister() {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry != null && this.registered) {
            try {
                registry.unregister(JoinAnnouncePlugin.MENU_FEATURE_ID);
            } catch (final RuntimeException ignored) {
                // 已经被别处摘掉
            }
        }
        this.registered = false;
    }
}
