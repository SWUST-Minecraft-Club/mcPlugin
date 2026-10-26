package com.example.town.market;

import com.example.town.TownPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;

/** 背包交付窗口期间冻结玩家物品操作；断线后按持久化意图恢复。 */
public final class MarketListener implements Listener {
    private final TownPlugin plugin;
    public MarketListener(TownPlugin plugin) {this.plugin=plugin;}
    private boolean frozen(Player p) {return plugin.market().frozen(p.getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onClick(InventoryClickEvent e) {if(e.getWhoClicked() instanceof Player p && frozen(p)) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onDrag(InventoryDragEvent e) {if(e.getWhoClicked() instanceof Player p && frozen(p)) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onOpen(InventoryOpenEvent e) {if(e.getPlayer() instanceof Player p && frozen(p)) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onDrop(PlayerDropItemEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onPickup(EntityPickupItemEvent e) {if(e.getEntity() instanceof Player p && frozen(p)) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onSwap(PlayerSwapHandItemsEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onConsume(PlayerItemConsumeEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onInteract(PlayerInteractEvent e) {if(frozen(e.getPlayer())) {e.setUseItemInHand(Event.Result.DENY);e.setUseInteractedBlock(Event.Result.DENY);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onUseEntity(PlayerInteractEntityEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onBreak(BlockBreakEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onPlace(BlockPlaceEvent e) {if(frozen(e.getPlayer())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onDamage(EntityDamageEvent e) {if(e.getEntity() instanceof Player p && frozen(p)) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onDeath(PlayerDeathEvent e) {if(frozen(e.getPlayer())) {e.setKeepInventory(true);e.getDrops().clear();}}
    @EventHandler public void onJoin(PlayerJoinEvent e) {plugin.market().resume(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e) {plugin.market().disconnected(e.getPlayer().getUniqueId());}
}
