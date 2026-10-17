package com.example.testplugin21;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 21：注册 1 个主菜单功能。
 *
 * <p>场景：无自定义位置，按注册顺序排进功能区
 */
public final class TestPlugin21 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin21:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.IRON_PICKAXE)
                .name("<yellow>测试功能 21</yellow>")
                .line("<gray>来自 TestPlugin21</gray>")
                .line("<dark_gray>无自定义位置，按注册顺序排进功能区</dark_gray>")
                .onClick((player, click) -> player.sendMessage(Component.text("测试功能 21 被点击：" + click.name())))
                .order(21)
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
