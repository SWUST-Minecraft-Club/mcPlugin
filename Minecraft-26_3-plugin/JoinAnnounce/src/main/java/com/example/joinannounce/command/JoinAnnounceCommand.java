package com.example.joinannounce.command;

import com.example.joinannounce.JoinAnnouncePlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * /joinannounce reload|preview —— 重载配置 / 自己先看一遍公告。
 */
public final class JoinAnnounceCommand {

    private JoinAnnounceCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(final JoinAnnouncePlugin plugin) {
        return Commands.literal("joinannounce")
                .requires(source -> source.getSender().hasPermission("joinannounce.admin"))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            plugin.reloadSettings();
                            context.getSource().getSender().sendMessage(
                                    plugin.message("messages.reloaded", "<green>JoinAnnounce 配置已重新加载。</green>"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("preview")
                        .executes(context -> {
                            plugin.announcements().previewTo(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
