package com.example.chatprivacy.command;

import com.example.chatprivacy.ChatPrivacyPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * /chatprivacy status|reload —— 查看运行状态 / 重载配置。
 */
public final class ChatPrivacyCommand {

    private ChatPrivacyCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(final ChatPrivacyPlugin plugin) {
        return Commands.literal("chatprivacy")
                .requires(source -> source.getSender().hasPermission("chatprivacy.admin"))
                .then(Commands.literal("status")
                        .executes(context -> {
                            plugin.sendStatus(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            plugin.reloadSettings();
                            context.getSource().getSender().sendMessage(plugin.settings().reloaded());
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(context -> {
                    plugin.sendStatus(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
