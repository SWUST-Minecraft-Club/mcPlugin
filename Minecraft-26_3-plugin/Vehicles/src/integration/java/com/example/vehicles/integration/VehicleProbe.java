package com.example.vehicles.integration;

import com.example.vehicles.Vehicles;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Local test fixture; never included in the production JAR. */
public final class VehicleProbe extends JavaPlugin {
    @Override public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("vtest", (BasicCommand) (source, args) -> {
                    var world = Bukkit.getWorlds().getFirst();
                    var plugin = JavaPlugin.getPlugin(Vehicles.class);
                    if (args.length > 0 && args[0].equals("components")) {
                        AtomicInteger remaining = new AtomicInteger(4), found = new AtomicInteger();
                        for (int cx = -1; cx <= 0; cx++) for (int cz = 0; cz <= 1; cz++) {
                            int x = cx, z = cz;
                            world.getChunkAtAsync(x, z, true).thenAccept(chunk ->
                                    Bukkit.getRegionScheduler().execute(this, world, x, z, () -> {
                                        for (var entity : chunk.getEntities()) {
                                            if (entity.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("vehicles", "car"))) found.incrementAndGet();
                                        }
                                        if (remaining.decrementAndGet() == 0) getLogger().info("VEHICLE_COMPONENTS " + found.get());
                                    }));
                        }
                        return;
                    }
                    if (args.length > 0 && args[0].equals("scene")) {
                        var remaining = new AtomicInteger(64);
                        for (int cx = -4; cx < 4; cx++) for (int cz = -2; cz < 6; cz++) {
                            int chunkX = cx, chunkZ = cz;
                            world.getChunkAtAsync(cx, cz, true).thenAccept(chunk ->
                                    Bukkit.getRegionScheduler().execute(this, world, chunkX, chunkZ, () -> {
                                        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                                            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                                                Material floor = Math.abs(x) <= 4 ? Material.SMOOTH_STONE : Material.GRASS_BLOCK;
                                                if (Math.abs(x) == 5) floor = Material.SMOOTH_QUARTZ;
                                                if (x == 0 && Math.floorMod(z, 9) < 4) floor = Material.PALE_OAK_PLANKS;
                                                world.getBlockAt(x, 64, z).setType(floor, false);
                                            }
                                        }
                                        if (remaining.decrementAndGet() == 0) getLogger().info("VEHICLE_SCENE_READY");
                                    }));
                        }
                        return;
                    }
                    if (args.length > 0 && args[0].equals("wall")) {
                        Bukkit.getRegionScheduler().execute(this, world, 0, 4, () -> {
                            for (int x = -4; x <= 4; x++) for (int y = 65; y <= 68; y++)
                                world.getBlockAt(x, y, 78).setType(Material.BRICKS, false);
                            getLogger().info("VEHICLE_WALL_READY");
                        });
                        return;
                    }
                    StringBuilder result = new StringBuilder("VEHICLE_STATE ");
                    for (var car : plugin.manager().active()) {
                        var state = car.state();
                        result.append("car=").append(car.id()).append(" colour=").append(car.colour())
                                .append(" owner=").append(car.owner()).append(" driver=").append(car.driver())
                                .append(" passenger=").append(car.passenger())
                                .append(String.format(java.util.Locale.ROOT, " x=%.3f y=%.3f z=%.3f yaw=%.2f speed=%.4f grounded=%s ",
                                        state.x(), state.y(), state.z(), state.yaw(), state.speed(), state.grounded()));
                    }
                    source.getSender().sendMessage(result.toString());
                    getLogger().info(result.toString());
                }));
    }
}
