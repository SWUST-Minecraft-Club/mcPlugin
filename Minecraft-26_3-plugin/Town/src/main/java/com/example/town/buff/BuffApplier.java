package com.example.town.buff;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.service.TownService;
import com.example.town.util.Threads;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 领地 buff 施加器：周期任务遍历在线玩家，处于本镇领地内的成员获得效果。
 * 效果时长 = 刷新间隔 × 2，离开领地后自然消退，无需主动移除。
 */
public final class BuffApplier {

    private final TownPlugin plugin;
    private final TownService service;
    private ScheduledTask scheduledTask;

    public BuffApplier(TownPlugin plugin, TownService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public synchronized void start() {
        stop();
        int interval = plugin.config().buffIntervalTicks();
        scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> tick(), interval, interval);
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public synchronized void restart() {
        start();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        int interval = plugin.config().buffIntervalTicks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var view = service.townAt(ChunkKey.of(player.getLocation()));
            if (view == null || !view.active()) {
                continue;
            }
            if (!view.members().containsKey(player.getUniqueId())) {
                continue;
            }
            for (var entry : view.buffs().entrySet()) {
                Long expiry = entry.getValue();
                if (expiry != -1 && expiry <= now) {
                    continue;
                }
                var def = buffDef(entry.getKey());
                if (def == null) {
                    continue;
                }
                Threads.forPlayer(plugin, player, () -> {
                    for (var effect : def.effects()) {
                        player.addPotionEffect(new PotionEffect(
                                effect.type(),
                                interval * 2,
                                effect.amplifier(),
                                true,
                                false,
                                false));
                    }
                });
            }
        }
    }

    private com.example.town.TownConfig.BuffDef buffDef(String id) {
        for (var def : plugin.config().buffs()) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }
}
