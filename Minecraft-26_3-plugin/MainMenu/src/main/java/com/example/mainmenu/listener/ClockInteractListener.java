package com.example.mainmenu.listener;

import com.example.mainmenu.MainMenuPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 手持时钟右键打开主菜单。
 *
 * <p>为了不影响正常游戏：
 * <ul>
 *     <li>默认潜行时不触发（可在配置里打开）；</li>
 *     <li>右键可交互方块（箱子、工作台等）时不抢事件，交给方块本身处理；</li>
 *     <li>主手优先，其次副手。</li>
 * </ul>
 */
public final class ClockInteractListener implements Listener {

    private final MainMenuPlugin plugin;

    public ClockInteractListener(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player player = event.getPlayer();
        if (player.isSneaking() && !plugin.getConfig().getBoolean("menu.open.clock-while-sneaking", false)) {
            return;
        }
        if (!holdingClock(player)) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && !handleBlockClick(event)) {
            return;
        }
        event.setCancelled(true);
        plugin.menus().open(player);
    }

    /**
     * 右键方块时是否由我们接管。
     *
     * <p>时钟本身没有右键功能，唯一要避让的是方块自己的交互（箱子、门、按钮…），
     * 否则玩家对着箱子点右键会同时打开箱子和主菜单。
     */
    private boolean handleBlockClick(final PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("menu.open.clock-right-click-on-block", true)) {
            return false;
        }
        final Block block = event.getClickedBlock();
        if (block == null) {
            return true;
        }
        // isInteractable() 已被标记为弃用（官方说明它不全面），但仍是唯一现成的启发式判断：
        // 宁可漏判（对着不认识的方块右键会开菜单），也不要误判成“不可交互”而抢掉箱子/门。
        return !isInteractable(block.getType());
    }

    @SuppressWarnings("deprecation")
    private static boolean isInteractable(final Material material) {
        return material.isInteractable();
    }

    private boolean holdingClock(final Player player) {
        if (player.getInventory().getItemInMainHand().getType() == clockMaterial()) {
            return true;
        }
        return player.getInventory().getItemInOffHand().getType() == clockMaterial();
    }

    private Material clockMaterial() {
        final Material material = Material.matchMaterial(plugin.string("menu.clock.material", "CLOCK"));
        return material == null || material.isAir() ? Material.CLOCK : material;
    }
}
