package com.example.mainmenu.listener;

import com.example.mainmenu.MainMenuPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 手持时钟右键打开主菜单。
 *
 * <p>对着空气（{@code RIGHT_CLICK_AIR}）和对着方块（{@code RIGHT_CLICK_BLOCK}）都会触发。
 * 为了不影响正常游戏：
 * <ul>
 *     <li>右键可交互方块（箱子、工作台等）时不抢事件，交给方块本身处理；</li>
 *     <li><b>主手或副手拿着时钟都算</b> —— 只认"拿着钟的那只手"会漏掉一种情况：
 *         对着空气右键时服务端只会为<b>主手</b>触发事件，钟在副手就打不开了；</li>
 *     <li>用 200ms 冷却避免主副手各触发一次导致界面被重复打开；</li>
 *     <li>潜行默认也允许（可用 {@code menu.open.clock-while-sneaking} 关掉）。</li>
 * </ul>
 */
public final class ClockInteractListener implements Listener {

    private static final long COOLDOWN_MILLIS = 200L;

    private final MainMenuPlugin plugin;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public ClockInteractListener(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.getConfig().getBoolean("menu.open.clock-right-click", true)) {
            return;
        }

        final Player player = event.getPlayer();
        if (player.isSneaking()
                && !plugin.getConfig().getBoolean("menu.open.clock-while-sneaking", true)) {
            return;
        }
        if (!holdingClock(player)) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && !handleBlockClick(event)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final Long last = lastOpen.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return;
        }
        lastOpen.put(player.getUniqueId(), now);

        event.setCancelled(true);
        plugin.getLogger().info("玩家 " + player.getName() + " 用时钟右键（" + action
                + "，手=" + event.getHand() + "）打开主菜单");
        plugin.menus().open(player);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        lastOpen.remove(event.getPlayer().getUniqueId());
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

    /** 主手或副手拿着时钟都算 */
    private boolean holdingClock(final Player player) {
        final Material clock = clockMaterial();
        return player.getInventory().getItemInMainHand().getType() == clock
                || player.getInventory().getItemInOffHand().getType() == clock;
    }

    private Material clockMaterial() {
        final Material material = Material.matchMaterial(plugin.string("menu.clock.material", "CLOCK"));
        return material == null || material.isAir() ? Material.CLOCK : material;
    }

    @SuppressWarnings("deprecation")
    private static boolean isInteractable(final Material material) {
        return material.isInteractable();
    }
}
