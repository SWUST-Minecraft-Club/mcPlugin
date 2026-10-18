package com.example.realtime;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RealTime：把"显示当前时间"作为主菜单的一个功能项。
 *
 * <p>以 MainMenu 为依赖（见 paper-plugin.yml 的 dependencies.server），在主菜单启用后注册功能。
 */
public final class RealTimePlugin extends JavaPlugin {

    private static final String FEATURE_ID = "realtime:clock";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MainMenuApi.registry().register(new TimeFeature(this));

        getComponentLogger().info(Component.text(
                "已启用：主菜单第 1 行第 2 格显示当前时间（东 "
                        + getConfig().getInt("time.zone-offset-hours", 8) + " 区）",
                NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        try {
            final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
            if (registry != null) {
                registry.unregister(FEATURE_ID);
            }
        } catch (final RuntimeException ignored) {
            // 主菜单可能已经先卸载了
        }
    }

    // ------------------------------------------------------------------ 配置辅助

    public String string(final String key, final String fallback) {
        final String value = getConfig().getString(key, fallback);
        return value == null ? fallback : value;
    }

    /** 读取配置里的 MiniMessage 文本 */
    public Component mini(final String key) {
        return MiniMessage.miniMessage().deserialize(string(key, ""));
    }

    /** 渲染一段 MiniMessage 文本 */
    public Component miniRaw(final String raw) {
        return MiniMessage.miniMessage().deserialize(raw);
    }
}
