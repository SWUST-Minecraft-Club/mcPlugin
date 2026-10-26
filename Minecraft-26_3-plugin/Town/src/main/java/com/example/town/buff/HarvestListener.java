package com.example.town.buff;

import com.example.town.TownPlugin;
import com.example.town.economy.CivicEffects;
import com.example.town.model.ChunkKey;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDropItemEvent;

/** 丰收只奖励成熟的原版作物产出；不复制种子、自定义方块或未成熟植株。 */
public final class HarvestListener implements Listener {
    private final TownPlugin plugin;
    private static final Set<Material> PRODUCE=Set.of(Material.WHEAT,Material.CARROT,Material.POTATO,Material.BEETROOT,
            Material.NETHER_WART,Material.COCOA_BEANS);
    public HarvestListener(TownPlugin plugin) {this.plugin=plugin;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void harvest(BlockDropItemEvent event) {
        if(!(event.getBlockState().getBlockData() instanceof Ageable age) || age.getAge()!=age.getMaximumAge()) return;
        var town=plugin.service().townAt(ChunkKey.of(event.getBlock().getLocation()));
        if(town==null || !town.active()) return;
        double bonus=CivicEffects.harvest(plugin.civic().catalog(),town.raw(),event.getPlayer().getUniqueId(),System.currentTimeMillis());
        if(bonus<=0) return;
        for(var drop:event.getItems()) {
            var item=drop.getItemStack();
            if(!PRODUCE.contains(item.getType()) || item.hasItemMeta()) continue;
            double expected=item.getAmount()*bonus;
            int extra=(int)Math.floor(expected)+(ThreadLocalRandom.current().nextDouble()<expected%1?1:0);
            if(extra>0) {item.setAmount(Math.min(item.getMaxStackSize(),item.getAmount()+extra));drop.setItemStack(item);}
        }
    }
}
