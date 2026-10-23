package com.example.mainmenu;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;
import com.example.mainmenu.command.CdCommand;
import com.example.mainmenu.debug.DebugReporter;
import com.example.mainmenu.internal.CustomFeatureStore;
import com.example.mainmenu.internal.FeatureRegistryImpl;
import com.example.mainmenu.internal.FeatureStateStore;
import com.example.mainmenu.listener.ClockInteractListener;
import com.example.mainmenu.listener.CustomClickListener;
import com.example.mainmenu.menu.AdminListener;
import com.example.mainmenu.menu.AdminService;
import com.example.mainmenu.menu.MenuListener;
import com.example.mainmenu.menu.MenuService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
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
    private CustomFeatureStore customFeatures;
    private FeatureRegistryImpl features;
    private MenuService menus;
    private AdminService admin;
    private DebugReporter debug;

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
        this.customFeatures = new CustomFeatureStore(this);
        this.customFeatures.load();
        this.features = new FeatureRegistryImpl(states);
        this.features.syncCustomFeatures(this.customFeatures.features());
        this.menus = new MenuService(this);
        this.admin = new AdminService(this);
        this.debug = new DebugReporter(this);

        MainMenuApi.install(features, menus::open);
        getServer().getServicesManager().register(
                MenuFeatureRegistry.class, features, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomClickListener(this), this);
        if (getConfig().getBoolean("menu.open.clock-right-click", true)) {
            getServer().getPluginManager().registerEvents(new ClockInteractListener(this), this);
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(CdCommand.create(this), "打开主菜单", List.of()));

        getComponentLogger().info(Component.text(
                "已启用（服务端 " + getServer().getMinecraftVersion() + "）；ESC 菜单按钮状态见 /cd debug",
                NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.shutdown();
        }
        MainMenuApi.uninstall();
        instance = null;
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

    public CustomFeatureStore customFeatures() {
        return customFeatures;
    }

    /** 管理页创建自定义功能并立即同步到功能注册表；名称为空时返回 null。 */
    public String createCustomFeature(final String name) {
        if (customFeatures == null || features == null) {
            return null;
        }
        final String id = customFeatures.create(name);
        if (id == null) {
            return null;
        }
        features.syncCustomFeatures(customFeatures.features());
        return id;
    }

    /** /cd reload：重新读取 custom-features.yml 并替换内存里的自定义功能。 */
    public void reloadCustomFeatures() {
        if (customFeatures == null || features == null) {
            return;
        }
        customFeatures.load();
        features.syncCustomFeatures(customFeatures.features());
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
            getLogger().warning("配置 " + path + ".material 无效：" + materialName + "，已回退为 STONE");
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
