package com.example.yudreamui.plugin;

import com.example.town.api.TownPageRenderer;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

/** Loaded only when Town is present. */
final class TownIntegration implements TownPageRenderer {
    private final LegacyPresentation legacy;
    TownIntegration(YudreamUiPlugin plugin, LegacyPresentation legacy) {
        this.legacy = legacy;
        Bukkit.getServicesManager().register(TownPageRenderer.class, this, plugin, ServicePriority.High);
    }
    public boolean open(Player p,String page,String source,UnaryOperator<String> resolve,Consumer<String> action) {return legacy.open(p,page,source,resolve,action);}
    public boolean active(Player p,String page) {return legacy.active(p,page);}
    public void close(Player p) {legacy.close(p);}
    public boolean suppliesClaimGrid(Player p) {return legacy.enabled() && p.hasPermission("yudreamui.use");}
    public boolean input(Player p,String title,String initial,int max,Consumer<String> submit,Runnable cancel) {return legacy.input(p,title,initial,max,submit,cancel);}
}
