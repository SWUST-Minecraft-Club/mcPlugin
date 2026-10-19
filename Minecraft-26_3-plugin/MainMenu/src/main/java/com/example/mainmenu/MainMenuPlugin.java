package com.example.mainmenu;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;
import com.example.mainmenu.command.CdCommand;
import com.example.mainmenu.debug.DebugReporter;
import com.example.mainmenu.internal.FeatureRegistryImpl;
import com.example.mainmenu.internal.FeatureStateStore;
import com.example.mainmenu.listener.ClockInteractListener;
import com.example.mainmenu.listener.CustomClickListener;
import com.example.mainmenu.menu.AdminHolder;
import com.example.mainmenu.menu.AdminListener;
import com.example.mainmenu.menu.AdminService;
import com.example.mainmenu.menu.MenuHolder;
import com.example.mainmenu.menu.MenuListener;
import com.example.mainmenu.menu.MenuService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 主菜单插件主类。
 *
 * <p>对外提供：
 * <ul>
 *     <li>{@link MainMenuApi}：其它插件注册功能的入口（同时注册进 Bukkit 服务系统）</li>
 *     <li>打开方式：ESC 菜单按钮（见 {@link MainMenuBootstrap}）、{@code /cd}、手持时钟右键</li>
 * </ul>
 */
public final class MainMenuPlugin extends JavaPlugin {

    private static volatile MainMenuPlugin instance;

    private FeatureStateStore states;
    private FeatureRegistryImpl features;
    private MenuService menus;
    private AdminService admin;
    private DebugReporter debug;
    /** 已经警告过的无效材质配置，避免菜单每次重绘都刷同一行日志 */
    private final Set<String> warnedMaterials = new HashSet<>();

    /** 供引导阶段注册的点击回调使用（那时插件实例还不存在） */
    public static MainMenuPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.states = new FeatureStateStore(this);
        this.states.load();
        this.features = new FeatureRegistryImpl(states);
        this.menus = new MenuService(this);
        this.admin = new AdminService(this);
        this.debug = new DebugReporter(this);

        MainMenuApi.install(features);
        getServer().getServicesManager().register(
                MenuFeatureRegistry.class, features, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomClickListener(this), this);
        // 时钟监听器始终注册：menu.open.clock-right-click 在每次事件里现读，
        // 这样 /cd reload 把开关从 false 改成 true 后不需要重启服务端。
        getServer().getPluginManager().registerEvents(new ClockInteractListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(CdCommand.create(this), "打开主菜单", List.of()));

        getComponentLogger().info(Component.text(
                "已启用（服务端 " + getServer().getMinecraftVersion() + "）；ESC 菜单按钮状态见 /cd debug",
                NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        // 先关掉玩家已打开的界面：监听器马上会被注销，界面留着的话里面的物品会被当成普通物品拿走
        closeOpenMenus();
        if (menus != null) {
            menus.shutdown();
        }
        if (admin != null) {
            admin.shutdown();
        }
        MainMenuApi.uninstall();
        instance = null;
    }

    /** 关闭所有由本插件打开的界面（主菜单与管理界面） */
    private void closeOpenMenus() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder || top.getHolder() instanceof AdminHolder) {
                player.closeInventory();
            }
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        warnedMaterials.clear();
    }

    // ------------------------------------------------------------------ 内部访问

    public FeatureRegistryImpl features() {
        return features;
    }

    public MenuService menus() {
        return menus;
    }

    public AdminService admin() {
        return admin;
    }

    public FeatureStateStore states() {
        return states;
    }

    public DebugReporter debug() {
        return debug;
    }

    // ------------------------------------------------------------------ 配置辅助

    public String string(final String key, final String fallback) {
        final String value = getConfig().getString(key, fallback);
        return value == null ? fallback : value;
    }

    /** 读取配置里的 MiniMessage 文本 */
    public Component mini(final String key, final TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(string(key, ""), resolvers);
    }

    /** 渲染一段 MiniMessage 文本 */
    public Component miniRaw(final String raw, final TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(raw, resolvers);
    }

    public void message(final CommandSender sender, final String key) {
        sender.sendMessage(mini(key));
    }

    /** 菜单标题（配置缺失时兜底） */
    public Component menuTitle() {
        final String raw = string("menu.title", "");
        return raw.isBlank() ? Component.text("主菜单") : miniRaw(raw);
    }

    /**
     * 按配置构造一个物品：{@code path.material} + {@code path.name}。
     * 材质无效时回退到石头并打日志。
     */
    public ItemStack configItem(final String path) {
        final String materialName = string(path + ".material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            if (warnedMaterials.add(path)) {
                getLogger().warning("配置 " + path + ".material 无效：" + materialName + "，已回退为 STONE");
            }
            material = Material.STONE;
        }
        final ItemStack item = new ItemStack(material);
        final String name = string(path + ".name", "");
        if (!name.isBlank()) {
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(miniRaw(name).decorationIfAbsent(
                        TextDecoration.ITALIC, TextDecoration.State.FALSE));
                item.setItemMeta(meta);
            }
        }
        return item;
    }
}
