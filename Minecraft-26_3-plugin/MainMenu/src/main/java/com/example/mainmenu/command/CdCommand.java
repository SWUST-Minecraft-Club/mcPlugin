package com.example.mainmenu.command;

import com.example.mainmenu.MainMenuPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /cd}：打开主菜单；{@code /cd reload} 重载配置；{@code /cd debug} 输出布局与 ESC 按钮状态。
 *
 * <p>ESC 菜单里的 dialog 按钮也走这条命令（{@code DialogAction.commandTemplate("/cd")}），
 * 所以这里刻意延迟 1 tick 再开界面：让客户端的 dialog 先关闭，避免开出来的界面被立刻顶掉。
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
                    plugin.getLogger().info("玩家 " + player.getName() + " 通过 /cd 打开主菜单"
                            + "（来源：" + context.getSource().getSender().getName() + "）");
                    // 延迟 1 tick：ESC 菜单里的 dialog 按钮也是执行本命令，
                    // 等客户端的 dialog 关闭后再开箱子界面，避免被顶掉
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> plugin.menus().open(player), 1L);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("mainmenu.reload"))
                        .executes(context -> {
                            plugin.reloadConfig();
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

    /** 保留给需要直接打开的场景（例如 dialog 的自定义点击回调） */
    public static void openNow(final MainMenuPlugin plugin, final Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.menus().open(player), 1L);
    }
}
