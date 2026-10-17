package com.example.testplugin02;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 02：注册 1 个主菜单功能。
 *
 * <p>场景：自定义位置：占用第 1 行第 9 格（非固定功能位）
 */
public final class TestPlugin02 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin02:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.DIAMOND)
                .name("<yellow>测试功能 02</yellow>")
                .line("<gray>来自 TestPlugin02</gray>")
                .line("<dark_gray>自定义位置：占用第 1 行第 9 格（非固定功能位）</dark_gray>")
                .preferredSlot(8)
                .order(2)
                .build());
    }

    @Override
    public void onDisable() {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry != null) {
            registry.unregister(FEATURE_ID);
        }
    }
}
