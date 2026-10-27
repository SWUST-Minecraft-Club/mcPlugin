package com.example.wallet.menu;

import com.example.wallet.WalletConfig;
import com.example.wallet.api.WalletService;
import com.example.wallet.WalletPlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * MainMenu 软依赖隔离层。
 *
 * <p>注意：本类对 MainMenu / 菜单功能相关类型全部走反射（连字段与局部变量都不出现这些类型）——
 * HotSpot 校验本类字节码时可能为验证可赋值性而加载 WalletFeature，进而解析其父接口
 * MenuFeature；MainMenu 缺失时直接 NoClassDefFoundError（2026.10.25 实测，见 docs/exp）。
 * 因此本类可安全地在 MainMenu 缺失时被加载，create() 返回 null 即可。
 */
public final class WalletMainMenuHook {

    private final WalletPlugin plugin;
    private final WalletConfig config;
    private final WalletService service;
    /** 反射构造的功能对象（com.example.wallet.menu.WalletFeature）。 */
    private Object feature;

    private WalletMainMenuHook(final WalletPlugin plugin, final WalletConfig config,
                               final WalletService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    /** MainMenu 未启用 / API 不可用时返回 null（本方法可安全地在缺失环境中调用）。 */
    public static WalletMainMenuHook create(final WalletPlugin plugin, final WalletConfig config,
                                            final WalletService service) {
        if (plugin.getServer().getPluginManager().getPlugin("MainMenu") == null) {
            return null;
        }
        try {
            // 触碰一次 MainMenu API，顺便验证类可达
            Class.forName("com.example.mainmenu.api.MainMenuApi");
            plugin.getLogger().info("已接入 MainMenu：注册钱包主菜单功能。");
            return new WalletMainMenuHook(plugin, config, service);
        } catch (final ClassNotFoundException | LinkageError e) {
            plugin.getLogger().warning("MainMenu API 类不可用，钱包主菜单功能禁用："
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    public void register() {
        final Object featureInstance = this.buildFeature();
        if (featureInstance == null) {
            return;
        }
        try {
            final Class<?> registryClass = Class.forName(
                    "com.example.mainmenu.api.MenuFeatureRegistry");
            final Class<?> featureClass = Class.forName("com.example.mainmenu.api.MenuFeature");
            final Object registry = Class.forName("com.example.mainmenu.api.MainMenuApi")
                    .getMethod("registry").invoke(null);
            final Method register = registryClass.getMethod("register", featureClass);
            register.invoke(registry, featureClass.cast(featureInstance));
            this.feature = featureInstance;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException e) {
            this.plugin.getLogger().warning("钱包主菜单功能注册失败：" + e);
        }
    }

    public void unregister() {
        if (this.feature == null) {
            return;
        }
        try {
            final Class<?> registryClass = Class.forName(
                    "com.example.mainmenu.api.MenuFeatureRegistry");
            final Class<?> featureClass = Class.forName("com.example.mainmenu.api.MenuFeature");
            final Object registry = Class.forName("com.example.mainmenu.api.MainMenuApi")
                    .getMethod("registryOrNull").invoke(null);
            if (registry == null) {
                return;
            }
            registryClass.getMethod("unregister", String.class)
                    .invoke(registry, "wallet:wallet");
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // MainMenu 可能已在关闭流程里
        } finally {
            this.feature = null;
        }
    }

    /** 反射构造 WalletFeature，复用已注册监听器的菜单实例。 */
    private Object buildFeature() {
        try {
            final Class<?> featureClass = Class.forName("com.example.wallet.menu.WalletFeature");
            final Class<?> menusClass = Class.forName("com.example.wallet.menu.WalletMenus");
            final Object menus = this.plugin.walletMenus();
            final Constructor<?> ctor = featureClass.getDeclaredConstructor(
                    WalletPlugin.class, WalletConfig.class, WalletService.class, menusClass);
            return ctor.newInstance(this.plugin, this.config, this.service, menus);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException e) {
            this.plugin.getLogger().warning("钱包菜单对象构造失败：" + e);
            return null;
        }
    }
}
