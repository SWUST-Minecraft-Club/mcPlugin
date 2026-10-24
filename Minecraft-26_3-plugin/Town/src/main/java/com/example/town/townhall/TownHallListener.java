package com.example.town.townhall;

import com.example.town.TownPlugin;
import com.example.town.model.Town;
import com.example.town.service.TownService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 镇政厅交互：
 * - 手持镇政厅道具右键方块 → 落成小镇中心（先于 CraftEngine 的放置机制拦截）；
 * - 右键镇政厅本体 → 打开小镇界面；
 * - 挖镇政厅本体 → 市长收回道具（数据层在 TownService）。
 */
public final class TownHallListener implements Listener {

    private final TownPlugin plugin;
    private final TownService service;
    private final TownHalls halls;

    public TownHallListener(TownPlugin plugin, TownService service, TownHalls halls) {
        this.plugin = plugin;
        this.service = service;
        this.halls = halls;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        // 手持镇政厅：抢在 CraftEngine / 原版放置之前处理，统一走服务层落成
        if (halls.isHallItem(event.getItem())) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            var target = event.getClickedBlock().getRelative(event.getBlockFace());
            service.placeHall(event.getPlayer(), target, result -> {
                service.deliver(event.getPlayer(), result);
                if (result.ok()) {
                    halls.consumeOne(event);
                }
            });
            return;
        }
        // 右键镇政厅本体：打开小镇界面
        Town hallTown = halls.hallAt(event.getClickedBlock());
        if (hallTown != null) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            plugin.openUi(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Town hallTown = halls.hallAt(event.getBlock());
        if (hallTown == null) {
            return;
        }
        event.setCancelled(true);
        service.breakHall(event.getPlayer(), result -> {
            service.deliver(event.getPlayer(), result);
            if (result.ok()) {
                halls.clearBlock(event.getBlock());
            }
        });
    }
}
