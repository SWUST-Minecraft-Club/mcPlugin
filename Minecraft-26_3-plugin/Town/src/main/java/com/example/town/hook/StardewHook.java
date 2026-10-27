package com.example.town.hook;

import com.example.town.TownPlugin;
import com.example.town.economy.CivicEffects;
import com.example.town.model.ChunkKey;
import org.bukkit.Bukkit;

/** Keep optional RptStardew types inside the lazily loaded adapter. */
public final class StardewHook implements AutoCloseable {
    private final Adapter adapter;
    private StardewHook(Adapter adapter) { this.adapter=adapter; }
    public static StardewHook create(TownPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("RptStardew")) return null;
        try {
            var adapter=Adapter.create(plugin);
            if(adapter==null) return null;
            plugin.getLogger().info("已接入星露谷技能经验接口。");
            return new StardewHook(adapter);
        } catch(LinkageError|RuntimeException failure) {
            plugin.getLogger().warning("星露谷经验联动不可用，需要 RptStardew 1.6+："+failure.getClass().getSimpleName());
            return null;
        }
    }
    public boolean available() { return adapter.available(); }
    @Override public void close() { adapter.close(); }
    private static final class Adapter {
        private final TownPlugin plugin;
        private final org.rptStardew.api.StardewProgressionApi api;
        private Adapter(TownPlugin plugin,org.rptStardew.api.StardewProgressionApi api) {this.plugin=plugin;this.api=api;}
        static Adapter create(TownPlugin plugin) {
            var api=Bukkit.getServicesManager().load(org.rptStardew.api.StardewProgressionApi.class);
            if(api==null) return null;
            var adapter=new Adapter(plugin,api);
            api.registerBonusProvider(plugin,adapter::bonus);
            return adapter;
        }
        boolean available() {
            return Bukkit.getPluginManager().isPluginEnabled("RptStardew")
                    && Bukkit.getServicesManager().load(org.rptStardew.api.StardewProgressionApi.class)==api;
        }
        double bonus(org.rptStardew.api.ExperienceContext context) {
            var world=Bukkit.getWorld(context.worldId());if(world==null) return 0;
            var town=plugin.service().townAt(new ChunkKey(world.getName(),context.x()>>4,context.z()>>4));
            return town==null?0:CivicEffects.experience(plugin.civic().catalog(),town.raw(),context.playerId(),
                    System.currentTimeMillis(),CivicEffects.Skill.valueOf(context.skill().name()));
        }
        void close() { api.unregisterBonusProvider(plugin); }
    }
}
