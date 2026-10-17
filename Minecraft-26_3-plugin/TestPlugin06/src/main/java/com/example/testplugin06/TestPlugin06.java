package com.example.testplugin06;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 06：注册 1 个主菜单功能。
 *
 * <p>场景：写在功能区格子上：按规范视为未指定位置，不算冲突
 */
public final class TestPlugin06 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin06:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.IRON_INGOT)
                .name("<yellow>测试功能 06</yellow>")
                .line("<gray>来自 TestPlugin06</gray>")
                .line("<dark_gray>写在功能区格子上：按规范视为未指定位置，不算冲突</dark_gray>")
                .preferredSlot(19)
                .order(6)
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
