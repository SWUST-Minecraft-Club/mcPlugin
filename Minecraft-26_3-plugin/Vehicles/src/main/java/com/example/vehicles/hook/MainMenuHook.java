package com.example.vehicles.hook;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeature;
import com.example.vehicles.Vehicles;
import org.bukkit.Material;

/** Isolated so the plugin can run without MainMenu. */
public final class MainMenuHook {
    private MainMenuHook() {}
    public static void register(Vehicles plugin) {
        MainMenuApi.registry().register(MenuFeature.builder("vehicles:garage")
                .owner("Vehicles").icon(Material.MINECART)
                .name("<color:#9bd5c2>小镇车库</color>")
                .line("<gray>双人漫游小车 · 薄荷绿 / 陶土橙 / 奶油白</gray>")
                .line("<gray>点击查看放车、驾驶与收车指令</gray>")
                .onClick((player, click) -> player.performCommand("vehicles")).build());
    }
    public static void unregister() {
        var registry = MainMenuApi.registryOrNull();
        if (registry != null) registry.unregister("vehicles:garage");
    }
}
