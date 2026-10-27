package com.example.vehicles;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Vehicles extends JavaPlugin implements Listener {
    private volatile VehicleSettings settings;
    private VehicleManager manager;
    private NamespacedKey entityKey;
    private boolean menuRegistered;

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = VehicleSettings.load(getConfig());
        entityKey = new NamespacedKey(this, "car");
        manager = new VehicleManager(this);
        try { VehicleResources.install(this); }
        catch (IOException error) {
            getLogger().severe("载具资源安装失败：" + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("vehicles", "小镇双人载具", List.of("car"), new VehicleCommand(this)));
        if (getServer().getPluginManager().isPluginEnabled("MainMenu")) {
            com.example.vehicles.hook.MainMenuHook.register(this);
            menuRegistered = true;
        }
        getLogger().info("小镇漫游车已启用：/vehicles spawn，三种配色，双人乘坐。");
    }

    @Override public void onDisable() {
        if (menuRegistered) com.example.vehicles.hook.MainMenuHook.unregister();
        if (manager != null) manager.shutdown();
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        TownCar car = manager.find(event.getRightClicked().getUniqueId());
        if (car == null) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("vehicles.use")) car.mount(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(EntityDamageEvent event) {
        if (manager.find(event.getEntity().getUniqueId()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exit(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        TownCar car = manager.find(event.getDismounted().getUniqueId());
        if (car != null) car.safeExit(player);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        // Vanilla removes a quitting player's root vehicle when it has only that player.
        // Detach first so a guest disconnect does not remove their seat and retire the car.
        var mount = event.getPlayer().getVehicle();
        if (mount != null && manager.find(mount.getUniqueId()) != null) event.getPlayer().leaveVehicle();
        TownCar car = manager.owned(event.getPlayer().getUniqueId());
        if (car != null) manager.retire(car);
    }

    public VehicleManager manager() { return manager; }
    VehicleSettings settings() { return settings; }
    NamespacedKey entityKey() { return entityKey; }
    void reloadSettings() { reloadConfig(); settings = VehicleSettings.load(getConfig()); }
    public static Component message(String message) { return MiniMessage.miniMessage().deserialize(message); }
    public static void tell(Player player, String text) {
        Component message = message(text);
        if (Bukkit.isOwnedByCurrentRegion(player)) player.sendMessage(message);
        else player.getScheduler().run(JavaPlugin.getPlugin(Vehicles.class), task -> player.sendMessage(message), null);
    }
}
