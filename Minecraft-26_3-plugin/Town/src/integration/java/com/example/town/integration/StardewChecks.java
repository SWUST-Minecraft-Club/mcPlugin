package com.example.town.integration;

import com.example.town.TownPlugin;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.rptStardew.*;
import org.rptStardew.api.*;

/** Loaded only when the optional core is installed; exercises the real shared service class. */
final class StardewChecks {
    static void run(TownPlugin town,UUID actor,BiConsumer<Boolean,String> check) {
        var core=(RptStardew)Bukkit.getPluginManager().getPlugin("RptStardew");
        var api=Bukkit.getServicesManager().load(StardewProgressionApi.class);
        check.accept(api==core.skillExperience(),"shared API identity across plugin classloaders");
        var world=Bukkit.getWorlds().getFirst();
        for(Skill skill:Skill.values()) {
            check.accept(api.bonus(new ExperienceContext(actor,skill,world.getUID(),1,64,1,100))==.25,skill+" member bonus inside town");
            check.accept(api.bonus(new ExperienceContext(actor,skill,world.getUID(),32,64,1,100))==0,skill+" outside town has no bonus");
            check.accept(api.bonus(new ExperienceContext(UUID.randomUUID(),skill,world.getUID(),1,64,1,100))==0,skill+" visitor has no bonus");
        }
        var at=new Location(world,1,64,1);
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "getUniqueId"->actor;case "getLocation"->at;default->throw new UnsupportedOperationException(method.getName());
        });
        for(Skill skill:Skill.values()) check.accept(core.skillExperience().experience(player,skill,at,100)==125,skill+" scales actual reward");
        var crop=core.crops().byId("parsnip");long before=core.farm().state().account(actor,0).xp();
        int base=FarmRules.harvestXp(core.farm().basePrice(crop));
        core.farm().harvested(crop,player,at);
        check.accept(core.farm().state().account(actor,0).xp()-before==Math.round(base*1.25),"farming award persists boosted experience");
        core.store().save();
    }
    static void expired(UUID actor,BiConsumer<Boolean,String> check) {
        var api=Bukkit.getServicesManager().load(StardewProgressionApi.class);
        for(Skill skill:Skill.values()) check.accept(api.bonus(new ExperienceContext(actor,skill,Bukkit.getWorlds().getFirst().getUID(),1,64,1,100))==0,skill+" expired policy grants no bonus");
    }
}
