package com.example.mcplugin263.command;

import com.example.mcplugin263.MCPlugin263;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * /hello [名字] —— 演示权限校验、可选参数与命令执行逻辑。
 */
public final class HelloCommand {

    private HelloCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(MCPlugin263 plugin) {
        return Commands.literal("hello")
                // 无权限时命令不会被补全，也无法执行
                .requires(source -> source.getSender().hasPermission("mcplugin263.hello"))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    String name = source.getSender() instanceof Player player ? player.getName() : "控制台";
                    plugin.send(source.getSender(), "messages.hello", Placeholder.unparsed("name", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            plugin.send(context.getSource().getSender(), "messages.hello",
                                    Placeholder.unparsed("name", name));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
