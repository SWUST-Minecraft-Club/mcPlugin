package com.example.testplugin03;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 测试用功能插件 03：注册 1 个主菜单功能。
 *
 * <p>场景：冲突用例：想占用时钟所在的固定功能位，应被改排到功能区
 */
public final class TestPlugin03 extends JavaPlugin {

    private static final String FEATURE_ID = "testplugin03:feature";

    @Override
    public void onEnable() {
        final MenuFeatureRegistry registry = MainMenuApi.registry();

        registry.register(MenuFeature.builder(FEATURE_ID)
                .owner(getPluginMeta().getName())
                .icon(Material.REDSTONE)
                .name("<yellow>测试功能 03</yellow>")
                .line("<gray>来自 TestPlugin03</gray>")
                .line("<dark_gray>冲突用例：想占用时钟所在的固定功能位，应被改排到功能区</dark_gray>")
                .onClick((player, click) -> player.sendMessage(Component.text("测试功能 03 被点击：" + click.name())))
                .preferredSlot(1)
                .order(3)
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
