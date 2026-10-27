package com.example.vehicles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Each component is only touched by its own Folia entity scheduler. */
public final class TownCar {
    private final Vehicles plugin;
    private final VehicleManager manager;
    private final UUID id = UUID.randomUUID();
    private final UUID owner;
    private final World world;
    private final String colour;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private final List<Part> parts = new ArrayList<>();
    private final Part[] seats = new Part[2];
    private volatile DrivePhysics.State state;
    private volatile UUID driver;
    private volatile UUID passenger;

    TownCar(Vehicles plugin, VehicleManager manager, Player owner, Location location, String colour) {
        this.plugin = plugin; this.manager = manager; this.owner = owner.getUniqueId();
        this.world = location.getWorld(); this.colour = colour;
        this.state = new DrivePhysics.State(location.x(), location.y(), location.z(), location.getYaw(), 0, 0, 0, false);
    }

    void spawn() {
        try {
            seats[0] = display(-.36, .48, -.17, null, false);
            seats[1] = display(.36, .48, -.17, null, false);
            display(0, .5, 0, "town_car_" + colour, false);
            for (double x : new double[]{-.76, .76}) for (double z : new double[]{-.82, .83}) {
                display(x, .3125, z, "town_wheel", z > 0);
            }
            for (double z : new double[]{-.62, .62}) {
                Interaction interaction = world.spawn(position(0, 0, z, state), Interaction.class, entity -> {
                    mark(entity);
                    entity.setInteractionWidth(1.75f);
                    entity.setInteractionHeight(1.72f);
                    entity.setResponsive(true);
                });
                parts.add(new Part(interaction, 0, 0, z, false));
            }
            for (Part part : parts) {
                manager.track(part.entity.getUniqueId(), this);
                part.entity.getScheduler().runAtFixedRate(plugin, task -> {
                    if (closed.get()) { part.entity.remove(); task.cancel(); return; }
                    if (part == seats[0]) tick();
                    if (closed.get()) { part.entity.remove(); task.cancel(); return; }
                    if (part == seats[0] || part == seats[1]) {
                        UUID rider = part.entity.getPassengers().stream().filter(Player.class::isInstance)
                                .map(Entity::getUniqueId).findFirst().orElse(null);
                        if (part == seats[0]) driver = rider; else passenger = rider;
                    }
                    part.update();
                }, () -> manager.retire(this), 1, 1);
            }
        } catch (RuntimeException exception) {
            for (Part part : parts) if (Bukkit.isOwnedByCurrentRegion(part.entity)) part.entity.remove();
            closed.set(true);
            throw exception;
        }
    }

    private Part display(double x, double y, double z, String model, boolean frontWheel) {
        ItemDisplay entity = world.spawn(position(x, y, z, state), ItemDisplay.class, display -> {
            mark(display);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            display.setDisplayWidth(5);
            display.setDisplayHeight(3);
            display.setViewRange(1.25f);
            if (model != null) {
                ItemStack item = new ItemStack(Material.PAPER);
                item.editMeta(meta -> meta.setItemModel(new NamespacedKey("vehicles", model)));
                display.setItemStack(item);
            }
            if (model != null && model.startsWith("town_car")) {
                display.setShadowRadius(1.25f);
                display.setShadowStrength(.5f);
            }
        });
        Part part = new Part(entity, x, y, z, frontWheel);
        part.wheel = "town_wheel".equals(model);
        parts.add(part);
        return part;
    }

    private void mark(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(plugin.entityKey(), PersistentDataType.STRING, id.toString());
    }

    private void tick() {
        if (seats[0].moving.get()) return;
        Player player = driver == null ? null : Bukkit.getPlayer(driver);
        DrivePhysics.Controls controls = DrivePhysics.Controls.NONE;
        boolean occupied = player != null && Bukkit.isOwnedByCurrentRegion(player)
                && player.getVehicle() == seats[0].entity;
        if (occupied) {
            var input = player.getCurrentInput();
            controls = new DrivePhysics.Controls(input.isForward(), input.isBackward(), input.isLeft(), input.isRight(), input.isJump());
        }
        var terrain = new VehicleTerrain(world, this::loadChunk);
        var previous = state;
        state = DrivePhysics.tick(previous, controls, occupied, plugin.settings().physics(), terrain);
        if (state.y() < world.getMinHeight() + 1) { manager.retire(this); return; }
        if (occupied && (seats[0].ticks++ % 5 == 0)) {
            player.sendActionBar(Vehicles.message("<color:#9bd5c2>小镇漫游车</color> <gray>│</gray> <white>"
                    + Math.round(Math.abs(state.speed()) * 72) + " km/h</white> <gray>│ 空格刹车 · Shift 下车</gray>"));
        }
        // Prefetch the next chunk without reading its blocks from the current region.
        if (occupied && Math.abs(state.speed()) > .02) {
            double direction = Math.toRadians(state.yaw());
            int x = ((int) Math.floor(state.x() - Math.sin(direction) * Math.signum(state.speed()) * 7)) >> 4;
            int z = ((int) Math.floor(state.z() + Math.cos(direction) * Math.signum(state.speed()) * 7)) >> 4;
            if (!world.isChunkLoaded(x, z)) loadChunk(((long) x << 32) | (z & 0xffffffffL));
        }
    }

    private void loadChunk(long key) {
        if (pendingChunks.size() >= 4 || !pendingChunks.add(key)) return;
        world.getChunkAtAsync((int) (key >> 32), (int) key, true)
                .whenComplete((chunk, error) -> pendingChunks.remove(key));
    }

    void mount(Player player) {
        int index = (player.getUniqueId().equals(owner) || player.hasPermission("vehicles.admin")) && driver == null ? 0 : 1;
        mount(player, index, 8);
    }

    private void mount(Player player, int index, int attempts) {
        Part seat = seats[index];
        seat.entity.getScheduler().runDelayed(plugin, task -> {
            if (closed.get()) return;
            if (!Bukkit.isOwnedByCurrentRegion(player)) {
                Vehicles.tell(player, "<yellow>请靠近车身后再试。</yellow>"); return;
            }
            if (!player.isOnline() || player.isDead() || player.isInsideVehicle()) return;
            if (player.getWorld() != world || player.getLocation().distanceSquared(seat.entity.getLocation()) > 25) {
                Vehicles.tell(player, "<yellow>请先靠近这辆车。</yellow>"); return;
            }
            if (!seat.entity.getPassengers().isEmpty()) {
                Vehicles.tell(player, "<yellow>座位已有人。驾驶位由车主使用。</yellow>"); return;
            }
            if (seat.moving.get()) {
                if (attempts > 0) mount(player, index, attempts - 1);
                return;
            }
            if (seat.entity.addPassenger(player)) {
                if (index == 0) driver = player.getUniqueId(); else passenger = player.getUniqueId();
                player.closeInventory();
                Vehicles.tell(player, index == 0
                        ? "<color:#9bd5c2>已坐上驾驶位。</color><gray>W/S 油门与倒车，A/D 转向，空格刹车，Shift 下车。</gray>"
                        : "<color:#9bd5c2>已坐上副驾驶。</color><gray>Shift 下车。</gray>");
            }
        }, null, 1);
    }

    void safeExit(Player player) {
        if (player.getUniqueId().equals(driver)) driver = null;
        if (player.getUniqueId().equals(passenger)) passenger = null;
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || player.isInsideVehicle() || player.getWorld() != world) return;
            var snapshot = state;
            var terrain = new VehicleTerrain(world, ignored -> {});
            for (double side : new double[]{1.55, -1.55}) {
                Location target = position(side, .04, -.2, snapshot);
                if (!Bukkit.isOwnedByCurrentRegion(target)) continue;
                double x = target.x(), y = target.y(), z = target.z();
                if (terrain.clear(new DrivePhysics.Bounds(x - .31, y, z - .31, x + .31, y + 1.85, z + .31))
                        && !terrain.clear(new DrivePhysics.Bounds(x - .3, y - .2, z - .3, x + .3, y + 1.7, z + .3))) {
                    target.setYaw(player.getYaw()); target.setPitch(player.getPitch());
                    player.teleportAsync(target);
                    return;
                }
            }
        }, null, 1);
    }

    Location position(double localX, double localY, double localZ, DrivePhysics.State snapshot) {
        double angle = Math.toRadians(snapshot.yaw());
        return new Location(world, snapshot.x() + Math.cos(angle) * localX - Math.sin(angle) * localZ,
                snapshot.y() + localY, snapshot.z() + Math.sin(angle) * localX + Math.cos(angle) * localZ,
                snapshot.yaw(), 0);
    }

    boolean close() { return closed.compareAndSet(false, true); }
    void shutdown() {
        close();
        // Folia's shutdown thread reports ownership after region world data has been retired.
        // Unsaved components must be left to the server's normal shutdown cleanup at that point.
        if (Bukkit.getServer().isStopping()) return;
        for (Part part : parts) {
            // Folia stops plugin schedulers during shutdown. Unsaved components vanish on restart.
            if (Bukkit.isOwnedByCurrentRegion(part.entity) && part.entity.isValid()) part.entity.remove();
        }
    }
    List<UUID> entityIds() { return parts.stream().map(part -> part.entity.getUniqueId()).toList(); }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public UUID driver() { return driver; }
    public UUID passenger() { return passenger; }
    public UUID worldId() { return world.getUID(); }
    public DrivePhysics.State state() { return state; }
    public String colour() { return colour; }

    private final class Part {
        final Entity entity;
        final double x, y, z;
        final boolean front;
        final AtomicBoolean moving = new AtomicBoolean();
        boolean wheel;
        int ticks, failures;
        float lastWheelAngle = Float.NaN, lastTurn = Float.NaN;
        Part(Entity entity, double x, double y, double z, boolean front) {
            this.entity = entity; this.x = x; this.y = y; this.z = z; this.front = front;
        }
        void update() {
            var snapshot = state;
            if (wheel && entity instanceof ItemDisplay display) {
                float turn = front && driver != null ? (float) Math.toRadians(Math.clamp(snapshot.speed() == 0 ? 0 : steering(), -25, 25)) : 0;
                float wheelAngle = (float) snapshot.wheelAngle();
                if (Float.compare(lastWheelAngle, wheelAngle) != 0 || Float.compare(lastTurn, turn) != 0) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(new Transformation(new Vector3f(),
                            new Quaternionf().rotateY(-turn).rotateX(wheelAngle), new Vector3f(1), new Quaternionf()));
                    lastWheelAngle = wheelAngle;
                    lastTurn = turn;
                }
            }
            Location target = position(x, y, z, snapshot);
            Location current = entity.getLocation();
            if (current.distanceSquared(target) < .000001 && Math.abs(current.getYaw() - target.getYaw()) < .001) return;
            if (!moving.compareAndSet(false, true)) return;
            entity.teleportAsync(target).whenComplete((success, error) -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    if (++failures >= 3) manager.retire(TownCar.this);
                } else failures = 0;
                moving.set(false);
            });
        }
        double steering() {
            Player player = Bukkit.getPlayer(driver);
            if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) return 0;
            var input = player.getCurrentInput();
            return ((input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0)) * 22;
        }
    }
}
