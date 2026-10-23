package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.util.Schedulers;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 打开菜单 / 翻页 / 保持固定格刷新。
 *
 * <p>Folia：所有背包操作都必须在该玩家所属区域线程执行；这里统一用
 * {@link Schedulers} 调到 {@code player.getScheduler()}。
 */
public final class MenuService {

    /** 同一个玩家在这个时间内重复请求开界面会被忽略（同一次点击被多条路径触发时只开一次） */
    private static final long OPEN_COOLDOWN_MILLIS = 200L;

    private final MainMenuPlugin plugin;
    private final MenuRenderer renderer;
    private final Map<UUID, ScheduledTask> refreshTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    public MenuService(final MainMenuPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new MenuRenderer(plugin);
    }

    public MenuRenderer renderer() {
        return renderer;
    }

    /** 打开主菜单（必须在该玩家的区域线程） */
    public void open(final Player player) {
        if (!Schedulers.ownedBy(player)) {
            Schedulers.runFor(plugin, player, () -> open(player));
            return;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastOpen.get(player.getUniqueId());
        if (last != null && now - last < OPEN_COOLDOWN_MILLIS) {
            return;
        }
        lastOpen.put(player.getUniqueId(), now);

        final MenuHolder holder = new MenuHolder(player.getUniqueId(), player.getName());
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE, plugin.menuTitle());
        holder.attach(inventory);
        holder.page(1);
        inventory.setContents(renderer.render(player, 1));
        player.openInventory(inventory);
        startRefresh(player, holder);
    }

    /** 切到某一页（必须在该玩家的区域线程） */
    public void show(final Player player, final MenuHolder holder, final int page) {
        if (!Schedulers.ownedBy(player)) {
            Schedulers.runFor(plugin, player, () -> show(player, holder, page));
            return;
        }
        holder.page(page);
        holder.getInventory().setContents(renderer.render(player, holder.page()));
    }

    /**
     * 菜单打开期间周期性整页重绘。
     *
     * <p>功能插件的 {@code icon()} / {@code description()} 每次渲染都会被重新调用，
     * 所以动态内容（例如 RealTime 插件的当前时间）会自动刷新。
     */
    private void startRefresh(final Player player, final MenuHolder holder) {
        stopRefresh(player.getUniqueId());
        final UUID uuid = player.getUniqueId();
        final long periodTicks = Math.max(1L, plugin.getConfig().getLong("menu.refresh-seconds", 1L)) * 20L;
        // 每个查看者一个实体调度任务：回调天然在该玩家的区域线程，可安全重绘背包
        final ScheduledTask task = Schedulers.runForAtFixedRate(plugin, player, () -> {
            final Player online = Bukkit.getPlayer(uuid);
            if (online == null
                    || !(online.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder open)
                    || open != holder) {
                stopRefresh(uuid);
                return;
            }
            final Inventory inventory = online.getOpenInventory().getTopInventory();
            inventory.setContents(renderer.render(online, holder.page()));
        }, periodTicks, periodTicks);
        refreshTasks.put(uuid, task);
    }

    public void stopRefresh(final UUID uuid) {
        final ScheduledTask task = refreshTasks.remove(uuid);
        Schedulers.cancel(task);
    }

    public void shutdown() {
        refreshTasks.values().forEach(Schedulers::cancel);
        refreshTasks.clear();
        lastOpen.clear();
    }
}
