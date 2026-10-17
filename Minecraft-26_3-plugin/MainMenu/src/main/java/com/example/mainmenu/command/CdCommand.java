package com.example.mainmenu.command;

import com.example.mainmenu.MainMenuPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /cd}：打开主菜单；{@code /cd reload} 重载配置；{@code /cd debug} 输出布局与 ESC 按钮状态。
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
                    plugin.menus().open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("mainmenu.reload"))
                        .executes(context -> {
                            plugin.reloadConfig();
                            plugin.playtime().invalidateAll();
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
}
