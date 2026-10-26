package com.example.mainmenu.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import java.util.function.Consumer;

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
    private static volatile Consumer<Player> opener;
    private static volatile MenuSidecardRegistry sidecards;

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

    /** Optional sidecards; registering never changes the administrator's default-off setting. */
    public static MenuSidecardRegistry sidecards() {
        final MenuSidecardRegistry current = sidecards;
        if (current == null) throw new IllegalStateException("MainMenu 侧卡注册表尚未加载");
        return current;
    }

    public static MenuSidecardRegistry sidecardsOrNull() { return sidecards; }

    /**
     * 替玩家打开主菜单（第一页）。
     *
     * <p>功能插件在自己的界面里做「返回上一级」时用：关闭自己的界面后调用它即可回到主菜单。
     *
     * @throws IllegalStateException 主菜单尚未加载
     */
    public static void open(final Player player) {
        final Consumer<Player> current = opener;
        if (current == null) {
            throw new IllegalStateException(
                    "MainMenu 尚未加载。请在 paper-plugin.yml 的 dependencies.server 中声明 MainMenu");
        }
        current.accept(player);
    }

    /** 替玩家打开主菜单；主菜单不可用时返回 false（软依赖场景，不抛异常）。 */
    public static boolean openOrNull(final Player player) {
        final Consumer<Player> current = opener;
        if (current == null || player == null) {
            return false;
        }
        current.accept(player);
        return true;
    }

    // ------------------------------------------------------------------
    // 以下为 MainMenu 内部使用
    // ------------------------------------------------------------------

    public static void install(final MenuFeatureRegistry implementation) {
        install(implementation, null);
    }

    public static void install(final MenuFeatureRegistry implementation, final Consumer<Player> openHandler) {
        install(implementation, openHandler, null);
    }

    public static void install(final MenuFeatureRegistry implementation, final Consumer<Player> openHandler,
                               final MenuSidecardRegistry sidecardRegistry) {
        registry = implementation;
        opener = openHandler;
        sidecards = sidecardRegistry;
    }

    public static void uninstall() {
        registry = null;
        opener = null;
        sidecards = null;
    }
}
