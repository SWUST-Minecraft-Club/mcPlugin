package com.example.testplugin04;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 04：注册 1 个主菜单功能。
 *
 * <p>场景：冲突用例：第 1 行第 1 格已被 TestPlugin01 占用，应被改排到功能区
 */
public final class TestPlugin04 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin04:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.GOLD_INGOT)
                .name("<yellow>测试功能 04</yellow>")
                .line("<gray>来自 TestPlugin04</gray>")
                .line("<dark_gray>冲突用例：第 1 行第 1 格已被 TestPlugin01 占用，应被改排到功能区</dark_gray>")
                .preferredSlot(0)
                .order(4)
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
