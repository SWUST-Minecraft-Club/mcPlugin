package com.example.vehicles;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class VehicleManager {
    public static final java.util.Set<String> COLOURS = java.util.Set.of("mint", "clay", "cream");
    private final Vehicles plugin;
    private final Map<UUID, TownCar> owners = new ConcurrentHashMap<>();
    private final Map<UUID, TownCar> components = new ConcurrentHashMap<>();

    VehicleManager(Vehicles plugin) { this.plugin = plugin; }

    public TownCar spawn(Player player, String colour) {
        if (!player.hasPermission("vehicles.use")) { Vehicles.tell(player, "<red>你没有使用载具的权限。</red>"); return null; }
        if (!COLOURS.contains(colour)) { Vehicles.tell(player, "<yellow>车色：mint（薄荷绿）、clay（陶土橙）、cream（奶油白）。</yellow>"); return null; }
        TownCar existing = owners.get(player.getUniqueId());
        if (existing != null) { Vehicles.tell(player, "<yellow>你已经有一辆车，使用 /vehicles ride 上车，或 /vehicles despawn 收车。</yellow>"); return null; }
        if (owners.size() >= plugin.settings().maximum()) { Vehicles.tell(player, "<yellow>当前放置的载具已达服务器上限。</yellow>"); return null; }
        Location base = player.getLocation();
        if (player.isInsideVehicle() || !player.isOnGround()) { Vehicles.tell(player, "<yellow>请先站在地面上再放置小车。</yellow>"); return null; }
        double angle = Math.toRadians(base.getYaw());
        Location target = base.clone().add(-Math.sin(angle) * 3.3, .1, Math.cos(angle) * 3.3);
        target.setPitch(0);
        var terrain = new VehicleTerrain(target.getWorld(), ignored -> {});
        if (!terrain.clear(DrivePhysics.bounds(target.x(), target.y(), target.z(), target.getYaw()))) {
            Vehicles.tell(player, "<yellow>前方空间不足，请找一处平整开阔的地方。</yellow>"); return null;
        }
        var support = DrivePhysics.bounds(target.x(), target.y() - .75, target.z(), target.getYaw());
        if (terrain.clear(support)) { Vehicles.tell(player, "<yellow>前方没有合适的地面，请靠近道路放置。</yellow>"); return null; }
        TownCar car = new TownCar(plugin, this, player, target, colour);
        // Creation is rare; synchronising only reservation avoids exceeding the global cap across regions.
        synchronized (owners) {
            if (owners.size() >= plugin.settings().maximum() || owners.putIfAbsent(player.getUniqueId(), car) != null) return null;
        }
        try { car.spawn(); }
        catch (RuntimeException error) { retire(car); throw error; }
        Vehicles.tell(player, "<color:#9bd5c2>小镇漫游车已停在前方。</color><gray>右键车身上车，或输入 /vehicles ride。</gray>");
        return car;
    }

    public void ride(Player player) {
        Location position = player.getLocation();
        TownCar nearest = owners.values().stream().filter(car -> car.worldId().equals(position.getWorld().getUID()))
                .filter(car -> distanceSquared(car, position) <= 25)
                .min(Comparator.comparingDouble(car -> distanceSquared(car, position))).orElse(null);
        if (nearest == null) Vehicles.tell(player, "<yellow>五格内没有可以乘坐的小车。</yellow>");
        else nearest.mount(player);
    }

    public void despawn(Player player) {
        TownCar car = owners.get(player.getUniqueId());
        if (car == null) Vehicles.tell(player, "<yellow>你还没有放置小车。</yellow>");
        else {
            retire(car);
            Vehicles.tell(player, "<color:#9bd5c2>小车已收回。</color>");
        }
    }

    void track(UUID entity, TownCar car) { components.put(entity, car); }
    public TownCar find(UUID entity) { return components.get(entity); }
    public TownCar owned(UUID owner) { return owners.get(owner); }
    public Collection<TownCar> active() { return java.util.List.copyOf(owners.values()); }
    void retire(TownCar car) {
        car.close();
        owners.remove(car.owner(), car);
        for (UUID entity : car.entityIds()) components.remove(entity, car);
    }
    void shutdown() { for (TownCar car : owners.values()) car.shutdown(); owners.clear(); components.clear(); }
    private static double distanceSquared(TownCar car, Location position) {
        var s = car.state();
        return Math.pow(s.x() - position.x(), 2) + Math.pow(s.y() - position.y(), 2) + Math.pow(s.z() - position.z(), 2);
    }
}
