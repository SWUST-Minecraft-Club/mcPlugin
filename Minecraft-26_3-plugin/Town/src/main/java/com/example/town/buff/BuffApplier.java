package com.example.town.buff;

import com.example.town.TownPlugin;
import com.example.town.economy.CivicEffects;
import com.example.town.model.ChunkKey;
import com.example.town.service.TownService;
import com.example.town.util.Threads;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.*;

/** 区域效果在实体线程读取与施加；政策受益范围、过期和同类效果上限统一计算。 */
public final class BuffApplier implements Listener {
    private final TownPlugin plugin;
    private final TownService service;
    private ScheduledTask scheduledTask;
    private final Map<UUID,Map<PotionEffectType,Integer>> owned=new ConcurrentHashMap<>();
    public BuffApplier(TownPlugin plugin,TownService service) {this.plugin=plugin;this.service=service;}
    public synchronized void start() {
        stop();int interval=plugin.config().buffIntervalTicks();
        scheduledTask=Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{
            for(Player player:Bukkit.getOnlinePlayers()) Threads.forPlayer(plugin,player,()->apply(player));
        },interval,interval);
    }
    public synchronized void stop() {
        if(scheduledTask!=null) {scheduledTask.cancel();scheduledTask=null;}
        if(plugin.isEnabled()) for(Player player:Bukkit.getOnlinePlayers()) Threads.forPlayer(plugin,player,()->clear(player,Map.of()));
        else owned.clear();
    }
    public synchronized void restart() {start();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        if(!ChunkKey.of(event.getFrom()).equals(ChunkKey.of(event.getTo()))) apply(event.getPlayer());
    }
    @EventHandler public void quit(PlayerQuitEvent event) {owned.remove(event.getPlayer().getUniqueId());}
    private void apply(Player player) {
        long now=System.currentTimeMillis();int duration=plugin.config().buffIntervalTicks()*2;
        Map<PotionEffectType,Integer> effects=new HashMap<>();
        var view=service.townAt(ChunkKey.of(player.getLocation()));
        if(view!=null && view.active()) {
            if(view.members().containsKey(player.getUniqueId())) for(var def:plugin.config().buffs()) {
                long expires=view.buffs().getOrDefault(def.id(),0L);
                if(expires!=-1 && expires<=now) continue;
                for(var effect:def.effects()) effects.merge(effect.type(),effect.amplifier(),Math::max);
            }
            for(var policy:plugin.civic().catalog().active(view.raw().civic(),now)) {
                if(policy.potion().isBlank() || !CivicEffects.benefits(policy.scope(),view.raw(),player.getUniqueId(),now)) continue;
                PotionEffectType type=PotionEffectType.getByName(policy.potion());
                if(type!=null) effects.merge(type,Math.min(2,policy.amplifier()),Math::max);
            }
        }
        clear(player,effects);
        Map<PotionEffectType,Integer> applied=new HashMap<>();
        for(var entry:effects.entrySet()) {
            var current=player.getPotionEffect(entry.getKey());
            if(current!=null && (current.getAmplifier()>entry.getValue()
                    || (current.getAmplifier()==entry.getValue() && current.getDuration()>duration))) continue;
            player.addPotionEffect(new PotionEffect(entry.getKey(),duration,entry.getValue(),true,false,false));
            applied.put(entry.getKey(),entry.getValue());
        }
        if(applied.isEmpty()) owned.remove(player.getUniqueId());else owned.put(player.getUniqueId(),Map.copyOf(applied));
    }
    private void clear(Player player,Map<PotionEffectType,Integer> wanted) {
        var previous=owned.getOrDefault(player.getUniqueId(),Map.of());
        for(var entry:previous.entrySet()) {
            if(wanted.containsKey(entry.getKey()) && wanted.get(entry.getKey()).equals(entry.getValue())) continue;
            var current=player.getPotionEffect(entry.getKey());
            if(current!=null && current.isAmbient() && current.getAmplifier()==entry.getValue()
                    && current.getDuration()<=plugin.config().buffIntervalTicks()*2) player.removePotionEffect(entry.getKey());
        }
        if(wanted.isEmpty()) owned.remove(player.getUniqueId());
    }
}
