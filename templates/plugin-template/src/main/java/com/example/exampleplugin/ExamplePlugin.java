package com.example.exampleplugin;

import com.example.exampleplugin.command.AdminCommand;
import com.example.exampleplugin.command.HelloCommand;
import com.example.exampleplugin.listener.PlayerJoinListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件主类：负责加载配置、注册命令与事件监听器。
 */
public final class ExamplePlugin extends JavaPlugin {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // 将 jar 内的 config.yml 释放到 plugins/ExamplePlugin/config.yml（文件已存在时不会覆盖）
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // Paper 插件不在 yml 中声明命令，而是通过生命周期事件注册 Brigadier 命令
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(HelloCommand.create(this), "打招呼示例命令", List.of("hi"));
            event.registrar().register(AdminCommand.create(this), "插件管理命令");
        });

        getComponentLogger().info(Component.text(
                "已启用，服务端版本：" + getServer().getMinecraftVersion(), NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        getComponentLogger().info(Component.text("已卸载", NamedTextColor.YELLOW));
    }

    /**
     * 读取 config.yml 中的 MiniMessage 文本并渲染成组件。
     *
     * @param key       配置路径，例如 messages.hello
     * @param resolvers 占位符，例如 Placeholder.unparsed("name", player.getName())
     */
    public Component render(String key, TagResolver... resolvers) {
        String raw = getConfig().getString(key, "<red>[ExamplePlugin] 缺少配置项：" + key);
        return miniMessage.deserialize(raw, resolvers);
    }

    /**
     * 向命令发送者发送一条配置文件中定义的消息。
     */
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(key, resolvers));
    }
}
