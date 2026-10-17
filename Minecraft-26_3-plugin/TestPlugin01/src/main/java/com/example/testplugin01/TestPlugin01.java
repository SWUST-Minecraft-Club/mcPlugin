package com.example.testplugin01;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 01：注册 1 个主菜单功能。
 *
 * <p>场景：自定义位置：占用第 1 行第 1 格（非固定功能位）
 */
public final class TestPlugin01 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin01:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.EMERALD)
                .name("<yellow>测试功能 01</yellow>")
                .line("<gray>来自 TestPlugin01</gray>")
                .line("<dark_gray>自定义位置：占用第 1 行第 1 格（非固定功能位）</dark_gray>")
                .onClick((player, click) -> player.sendMessage(Component.text("测试功能 01 被点击：" + click.name())))
                .preferredSlot(0)
                .order(1)
                .build());

        registry.register(MenuFeature.builder("testplugin01:extra")
                .owner(getPluginMeta().getName())
                .icon(Material.CLOCK)
                .name("<aqua>测试功能 01-2</aqua>")
                .line("<gray>同一个插件注册的第二个功能</gray>")
                .line("<dark_gray>没有自定义位置，排进功能区</dark_gray>")
                .order(1)
                .build());
    }

    @Override
    public void onDisable() {
        unregister(FEATURE_ID, "testplugin01:extra");
    }

    private void unregister(final String... ids) {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry == null) {
            return;
        }
        for (final String id : ids) {
            registry.unregister(id);
        }
    }
}
