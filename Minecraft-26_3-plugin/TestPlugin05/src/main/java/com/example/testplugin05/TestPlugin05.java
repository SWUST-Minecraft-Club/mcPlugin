package com.example.testplugin05;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 05：注册 1 个主菜单功能。
 *
 * <p>场景：冲突用例：想占用在线时长所在的固定功能位，应被改排到功能区
 */
public final class TestPlugin05 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin05:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.LAPIS_LAZULI)
                .name("<yellow>测试功能 05</yellow>")
                .line("<gray>来自 TestPlugin05</gray>")
                .line("<dark_gray>冲突用例：想占用在线时长所在的固定功能位，应被改排到功能区</dark_gray>")
                .onClick((player, click) -> player.sendMessage(Component.text("测试功能 05 被点击：" + click.name())))
                .preferredSlot(4)
                .order(5)
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
