package com.example.mainmenu.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;

/**
 * 主菜单对外入口。
 *
 * <p>功能插件的用法：
 * <pre>{@code
 * // 1) paper-plugin.yml 里声明依赖，保证 MainMenu 先加载
 * // dependencies:
 * //   server:
 * //     MainMenu:
 * //       load: BEFORE
 * //       required: true
 *
 * // 2) build.gradle 里编译期依赖（同一仓库内直接依赖工程）
 * // compileOnly project(':MainMenu')
 *
 * // 3) onEnable 里注册
 * MainMenuApi.registry().register(
 *     MenuFeature.builder("shop:main")
 *         .icon(Material.EMERALD)
 *         .name("<green>商店</green>")
 *         .line("<gray>点击打开商店</gray>")
 *         .onClick((player, click) -> player.sendMessage(Component.text("打开商店")))
 *         .build()
 * );
 * }</pre>
 *
 * <p>也可以走 Bukkit 服务系统：{@code Bukkit.getServicesManager().load(MenuFeatureRegistry.class)}。
 */
public final class MainMenuApi {

    private static volatile MenuFeatureRegistry registry;

    private MainMenuApi() {
    }

    /** 主菜单是否已加载（未加载时不要注册） */
    public static boolean isAvailable() {
        return registry != null;
    }

    /**
     * 取得功能注册表。
     *
     * @throws IllegalStateException 主菜单尚未加载（通常是没在 paper-plugin.yml 里声明依赖）
     */
    public static MenuFeatureRegistry registry() {
        final MenuFeatureRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException(
                    "MainMenu 尚未加载。请在 paper-plugin.yml 的 dependencies.server 中声明 MainMenu（required: true）");
        }
        return current;
    }

    /** 通过 Bukkit 服务系统查询（等价于 {@link #registry()}，未注册时返回 null） */
    public static MenuFeatureRegistry registryOrNull() {
        final ServicesManager services = Bukkit.getServicesManager();
        return services.load(MenuFeatureRegistry.class);
    }

    // ------------------------------------------------------------------
    // 以下为 MainMenu 内部使用
    // ------------------------------------------------------------------

    public static void install(final MenuFeatureRegistry implementation) {
        registry = implementation;
    }

    public static void uninstall() {
        registry = null;
    }
}
