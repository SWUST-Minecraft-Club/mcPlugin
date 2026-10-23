package com.example.mainmenu.command;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.util.Schedulers;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /cd}：打开主菜单；{@code /cd reload} 重载配置；{@code /cd debug} 输出布局与 ESC 按钮状态。
 *
 * <p>{@code /cd clockair} 是给客户端模组用的：服务端不会为"手持非可用物品右键空气"触发
 * {@code PlayerInteractEvent}（已 javap 服务端 jar 确认），所以由客户端模组检测这种点击后调用本子命令，
 * 服务端在这里校验是否手持时钟。
 *
 * <p>打开界面统一延迟 1 tick：ESC 菜单里的 dialog 按钮也走这条路径，等客户端的 dialog 关闭后再开箱子界面，
 * 避免刚打开的界面被顶掉。
 */
public final class CdCommand {

    private CdCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(final MainMenuPlugin plugin) {
        return Commands.literal("cd")
                .requires(source -> source.getSender().hasPermission("mainmenu.use"))
                .executes(context -> {
                    if (!(context.getSource().getSender() instanceof Player player)) {
                        plugin.message(context.getSource().getSender(), "messages.players-only");
                        return 0;
                    }
                    openLater(plugin, player, "/cd");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("clockair")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                return 0;
                            }
                            if (!holdingClock(plugin, player)) {
                                return 0;
                            }
                            openLater(plugin, player, "/cd clockair（时钟右键空气）");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("mainmenu.reload"))
                        .executes(context -> {
                            plugin.reloadConfig();
                            plugin.reloadCustomFeatures();
                            plugin.message(context.getSource().getSender(), "messages.reloaded");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("debug")
                        .requires(source -> source.getSender().hasPermission("mainmenu.reload"))
                        .executes(context -> {
                            plugin.debug().report(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /** 供 dialog 的自定义点击回调复用 */
    public static void openNow(final MainMenuPlugin plugin, final Player player) {
        openLater(plugin, player, "ESC 菜单按钮");
    }

    private static void openLater(final MainMenuPlugin plugin, final Player player, final String source) {
        plugin.getLogger().info("玩家 " + player.getName() + " 打开主菜单（来源：" + source + "）");
        // 背包界面只能在玩家所属区域线程打开；实体调度器会落到正确的线程
        Schedulers.runForDelayed(plugin, player, () -> plugin.menus().open(player), 1L);
    }

    /** 主手或副手拿着时钟都算 */
    private static boolean holdingClock(final MainMenuPlugin plugin, final Player player) {
        final Material configured = Material.matchMaterial(plugin.string("menu.open.clock-material", "CLOCK"));
        final Material clock = configured == null || configured.isAir() ? Material.CLOCK : configured;
        return player.getInventory().getItemInMainHand().getType() == clock
                || player.getInventory().getItemInOffHand().getType() == clock;
    }
}
