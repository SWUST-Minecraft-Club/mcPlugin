package com.example.town.relocation;

import com.example.town.TownPlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import io.papermc.paper.event.entity.EntityMoveEvent;

/** 施工锁独立于可关闭的领土保护，管理员 bypass 也不能打开施工容器。 */
public final class ConstructionListener implements Listener {
    private final TownPlugin plugin;
    public ConstructionListener(TownPlugin plugin) {this.plugin=plugin;}
    private boolean at(Location l) {return l!=null && plugin.relocation().locks().locked(l.getWorld().getName(),l.getBlockX(),l.getBlockY(),l.getBlockZ());}
    private boolean at(Block b) {return at(b.getLocation());}
    private boolean inventory(Inventory i) {
        InventoryHolder h=i.getHolder();
        if(h instanceof DoubleChest d) return holder(d.getLeftSide()) || holder(d.getRightSide());
        return holder(h) || at(i.getLocation());
    }
    private boolean holder(InventoryHolder h) {return h instanceof BlockInventoryHolder b && at(b.getBlock());}
    private boolean entering(Location from,Location to) {return at(to) && !at(from);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onBreak(BlockBreakEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onPlace(BlockPlaceEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onInteract(PlayerInteractEvent e) {
        if(e.getClickedBlock()!=null && at(e.getClickedBlock())) {e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onOpen(InventoryOpenEvent e) {if(inventory(e.getInventory())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onClick(InventoryClickEvent e) {if(inventory(e.getView().getTopInventory())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onDrag(InventoryDragEvent e) {if(inventory(e.getView().getTopInventory())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onHopper(InventoryMoveItemEvent e) {if(inventory(e.getSource()) || inventory(e.getDestination())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onPickup(InventoryPickupItemEvent e) {if(inventory(e.getInventory()) || at(e.getItem().getLocation())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onEmpty(PlayerBucketEmptyEvent e) {if(at(e.getBlock()) || at(e.getBlock().getRelative(e.getBlockFace()))) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onFill(PlayerBucketFillEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onExplosion(EntityExplodeEvent e) {e.blockList().removeIf(this::at);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onExplosion(BlockExplodeEvent e) {e.blockList().removeIf(this::at);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onExtend(BlockPistonExtendEvent e) {
        if(at(e.getBlock()) || e.getBlocks().stream().anyMatch(b->at(b) || at(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onRetract(BlockPistonRetractEvent e) {
        if(at(e.getBlock()) || e.getBlocks().stream().anyMatch(b->at(b) || at(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onFluid(BlockFromToEvent e) {if(at(e.getBlock()) || at(e.getToBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onPhysics(BlockPhysicsEvent e) {if(at(e.getBlock()) || at(e.getSourceBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onGrow(BlockGrowEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onForm(BlockFormEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onSpread(BlockSpreadEvent e) {if(at(e.getBlock()) || at(e.getSource())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onFade(BlockFadeEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onBurn(BlockBurnEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onIgnite(BlockIgniteEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onDispense(BlockDispenseEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onCook(BlockCookEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onBrew(BrewEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onSmelt(FurnaceBurnEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onEntityBlock(EntityChangeBlockEvent e) {if(at(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onSpawn(EntitySpawnEvent e) {
        if(at(e.getLocation()) && !(plugin.relocation().internalFurniture(e.getLocation()) && plugin.craftEngine()!=null && plugin.craftEngine().furnitureEntity(e.getEntity()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onEntityMove(EntityMoveEvent e) {if(entering(e.getFrom(),e.getTo())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onMove(PlayerMoveEvent e) {if(entering(e.getFrom(),e.getTo()) && !plugin.relocation().mayInspect(e.getPlayer().getUniqueId(),e.getTo())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onTeleport(PlayerTeleportEvent e) {if(entering(e.getFrom(),e.getTo()) && !plugin.relocation().mayInspect(e.getPlayer().getUniqueId(),e.getTo())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onEntityUse(PlayerInteractEntityEvent e) {if(at(e.getRightClicked().getLocation())) e.setCancelled(true);}
    @EventHandler public void onQuit(PlayerQuitEvent e) {if(plugin.relocation().draft(e.getPlayer().getUniqueId())!=null) try {plugin.relocation().cancel(e.getPlayer());}catch(IllegalArgumentException ignored){}}
}
