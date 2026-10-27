package com.example.vehicles;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class VehicleCommand implements BasicCommand {
    private final Vehicles plugin;
    VehicleCommand(Vehicles plugin) { this.plugin = plugin; }

    @Override public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("vehicles.admin")) { sender.sendMessage(Vehicles.message("<red>你没有管理载具的权限。</red>")); return; }
            plugin.reloadSettings();
            sender.sendMessage(Vehicles.message("<color:#9bd5c2>载具配置已重载。</color>")); return;
        }
        if (sub.equals("status")) {
            sender.sendMessage(Vehicles.message("<color:#9bd5c2>Vehicles " + plugin.getPluginMeta().getVersion()
                    + "</color><gray> · 当前 " + plugin.manager().active().size() + " 辆车</gray>"));
            if (sender instanceof Player player) {
                TownCar car = plugin.manager().owned(player.getUniqueId());
                if (car != null) {
                    var state = car.state();
                    player.sendMessage(Vehicles.message("<gray>你的小车：" + car.colour() + " · "
                            + Math.round(state.x()) + ", " + Math.round(state.y()) + ", " + Math.round(state.z())
                            + " · " + Math.round(Math.abs(state.speed()) * 72) + " km/h</gray>"));
                }
            }
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Vehicles.message("<yellow>玩家命令：/vehicles spawn、ride、leave、despawn；控制台可用 status、reload。</yellow>")); return;
        }
        switch (sub) {
            case "spawn" -> plugin.manager().spawn(player, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "mint");
            case "ride" -> plugin.manager().ride(player);
            case "leave" -> {
                if (player.getVehicle() != null && plugin.manager().find(player.getVehicle().getUniqueId()) != null) player.leaveVehicle();
                else Vehicles.tell(player, "<yellow>你没有乘坐小车。</yellow>");
            }
            case "despawn" -> plugin.manager().despawn(player);
            default -> {
                Vehicles.tell(player, "<color:#9bd5c2><bold>小镇漫游车</bold></color><gray> · 双人复古小车</gray>");
                Vehicles.tell(player, "<white>/vehicles spawn [mint|clay|cream]</white><gray> 放置小车</gray>");
                Vehicles.tell(player, "<white>/vehicles ride</white><gray> 上车　</gray><white>/vehicles despawn</white><gray> 收车</gray>");
                Vehicles.tell(player, "<gray>W/S 油门与倒车 · A/D 转向 · 空格刹车 · Shift 下车；车主驾驶，访客乘坐。</gray>");
            }
        }
    }

    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> options = args.length == 2 && args[0].equalsIgnoreCase("spawn")
                ? List.of("mint", "clay", "cream") : args.length <= 1
                ? source.getSender().hasPermission("vehicles.admin")
                    ? List.of("spawn", "ride", "leave", "despawn", "status", "reload")
                    : List.of("spawn", "ride", "leave", "despawn", "status") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
    @Override public String permission() { return "vehicles.use"; }
}
