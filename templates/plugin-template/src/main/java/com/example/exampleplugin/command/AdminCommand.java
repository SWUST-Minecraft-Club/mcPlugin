package com.example.exampleplugin.command;

import com.example.exampleplugin.ExamplePlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * /mcplugin reload|info —— 演示子命令与配置热重载。
 */
public final class AdminCommand {

    private AdminCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(ExamplePlugin plugin) {
        return Commands.literal("mcplugin")
                .requires(source -> source.getSender().hasPermission("exampleplugin.admin"))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("exampleplugin.reload"))
                        .executes(context -> {
                            plugin.reloadConfig();
                            plugin.send(context.getSource().getSender(), "messages.reloaded");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("info")
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(Component.text()
                                    .append(Component.text(plugin.getPluginMeta().getName(), NamedTextColor.AQUA))
                                    .append(Component.text(" v" + plugin.getPluginMeta().getVersion(),
                                            NamedTextColor.WHITE))
                                    .append(Component.text(" | API " + plugin.getPluginMeta().getAPIVersion(),
                                            NamedTextColor.GRAY))
                                    .append(Component.text(" | 服务端 " + plugin.getServer().getMinecraftVersion(),
                                            NamedTextColor.GRAY))
                                    .build());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
