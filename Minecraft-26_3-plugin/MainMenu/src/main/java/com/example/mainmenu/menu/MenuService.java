package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * 打开菜单 / 翻页 / 保持固定格刷新。
 */
public final class MenuService {

    private final MainMenuPlugin plugin;
    private final MenuRenderer renderer;
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    public MenuService(final MainMenuPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new MenuRenderer(plugin);
    }

    public MenuRenderer renderer() {
        return renderer;
    }

    /** 打开主菜单（必须主线程） */
    public void open(final Player player) {
        final MenuHolder holder = new MenuHolder(player.getUniqueId(), player.getName());
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE, plugin.menuTitle());
        holder.attach(inventory);
        holder.page(1);
        inventory.setContents(renderer.render(player, 1));
        player.openInventory(inventory);
        startRefresh(player, holder);
    }

    /** 切到某一页 */
    public void show(final Player player, final MenuHolder holder, final int page) {
        holder.page(page);
        holder.getInventory().setContents(renderer.render(player, holder.page()));
    }

    /** 菜单打开期间每秒刷新时钟与在线时长格（这两格的内容会变） */
    private void startRefresh(final Player player, final MenuHolder holder) {
        stopRefresh(player.getUniqueId());
        final UUID uuid = player.getUniqueId();
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final Player online = Bukkit.getPlayer(uuid);
            if (online == null
                    || !(online.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder open)
                    || open != holder) {
                stopRefresh(uuid);
                return;
            }
            final Inventory inventory = online.getOpenInventory().getTopInventory();
            inventory.setItem(MenuLayout.CLOCK_SLOT, renderer.clockItem());
            inventory.setItem(MenuLayout.PAINTING_SLOT, renderer.paintingItem(online));
        }, 20L, 20L);
        refreshTasks.put(uuid, task);
    }

    public void stopRefresh(final UUID uuid) {
        final BukkitTask task = refreshTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        refreshTasks.values().forEach(BukkitTask::cancel);
        refreshTasks.clear();
    }
}
