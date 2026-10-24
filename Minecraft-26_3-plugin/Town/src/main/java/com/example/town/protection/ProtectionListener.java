package com.example.town.protection;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.service.TownService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 小镇领地保护。所有事件都在方块所属区域线程回调；
 * 判定 = 该区块属于某个已成立小镇 && 玩家非成员 && 无 town.bypass。
 */
public final class ProtectionListener implements Listener {

    /** 被拒提示的节流间隔（毫秒）。 */
    private static final long WARN_INTERVAL = 1500L;

    private final TownPlugin plugin;
    private final TownService service;
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    public ProtectionListener(TownPlugin plugin, TownService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ------------------------------------------------------------------
    // 方块
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.config().protection().breakBlocks()
                && deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.config().protection().place()
                && deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            // 踩耕地 / 压力板：静默取消，不发提示
            if (plugin.config().protection().interact()
                    && deny(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && plugin.config().protection().interact()
                && deny(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            warn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !plugin.config().protection().container()) {
            return;
        }
        Location location = containerLocation(event.getInventory().getHolder());
        if (location != null && deny(player, location)) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.config().protection().bucket()
                && deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.config().protection().bucket()
                && deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    // ------------------------------------------------------------------
    // 爆炸 / 活塞 / 火
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.config().protection().explosion()) {
            return;
        }
        event.blockList().removeIf(block -> inClaim(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.config().protection().explosion()) {
            return;
        }
        event.blockList().removeIf(block -> inClaim(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.config().protection().piston()) {
            return;
        }
        if (pistonCrosses(event.getBlocks(), event.getDirection())
                || inClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.config().protection().piston()) {
            return;
        }
        if (pistonCrosses(event.getBlocks(), event.getDirection())
                || inClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonCrosses(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (inClaim(block.getLocation())
                    || inClaim(block.getRelative(direction).getLocation())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!plugin.config().protection().fire() || !inClaim(event.getBlock().getLocation())) {
            return;
        }
        if (event.getPlayer() == null || deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (!plugin.config().protection().fire()) {
            return;
        }
        if (event.getSource().getType() == org.bukkit.Material.FIRE
                && inClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // 展示物 / 实体破坏
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (plugin.config().protection().hanging()
                && event.getPlayer() != null
                && deny(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.config().protection().hanging()) {
            return;
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                && inClaim(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByPlayer(HangingBreakByEntityEvent event) {
        if (!plugin.config().protection().hanging()
                || !(event.getRemover() instanceof Player player)) {
            return;
        }
        if (deny(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (plugin.config().protection().hanging()
                && deny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.config().protection().hanging()
                || !(event.getDamager() instanceof Player player)) {
            return;
        }
        Entity target = event.getEntity();
        boolean decorative = target instanceof ItemFrame
                || target instanceof org.bukkit.entity.GlowItemFrame
                || target instanceof Painting
                || target instanceof ArmorStand;
        if (decorative && deny(player, target.getLocation())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.config().protection().entityGrief()
                && !(event.getEntity() instanceof Player)
                && inClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private boolean deny(Player player, Location location) {
        return service.denied(player, location);
    }

    private boolean inClaim(Location location) {
        return service.townAt(ChunkKey.of(location)) != null;
    }

    private Location containerLocation(InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder blockHolder) {
            return blockHolder.getBlock().getLocation();
        }
        if (holder instanceof DoubleChest chest) {
            return chest.getLocation();
        }
        return null;
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(player.getUniqueId());
        if (last != null && now - last < WARN_INTERVAL) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        Component message = plugin.messages().render("protection-denied");
        com.example.town.util.Threads.forPlayer(plugin, player,
                () -> player.sendActionBar(message));
    }
}
