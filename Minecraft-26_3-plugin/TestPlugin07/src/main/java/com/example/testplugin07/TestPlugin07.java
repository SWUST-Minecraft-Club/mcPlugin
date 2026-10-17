package com.example.testplugin07;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 07：注册 1 个主菜单功能。
 *
 * <p>场景：无自定义位置，按注册顺序排进功能区
 */
public final class TestPlugin07 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin07:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.COAL)
                .name("<yellow>测试功能 07</yellow>")
                .line("<gray>来自 TestPlugin07</gray>")
                .line("<dark_gray>无自定义位置，按注册顺序排进功能区</dark_gray>")
                .onClick((player, click) -> player.sendMessage(Component.text("测试功能 07 被点击：" + click.name())))
                .order(7)
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
