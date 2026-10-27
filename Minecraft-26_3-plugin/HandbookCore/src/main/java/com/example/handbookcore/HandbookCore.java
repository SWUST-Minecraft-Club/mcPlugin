package com.example.handbookcore;

import com.example.handbookcore.api.HandbookService;
import com.example.handbookcore.hook.*;
import com.example.handbookcore.internal.*;
import com.example.handbookcore.render.HandbookViews;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class HandbookCore extends JavaPlugin implements Listener {
    private final HandbookCatalog catalog = new HandbookCatalog();
    private HandbookViews views;
    private MainMenuBridge menu;
    private CraftEngineBridge ce;
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            var backup = ContentBootstrap.prepare(getDataFolder().toPath());
            if (backup != null) getLogger().info("已清理核心自带的玩法示例；原文件备份到 " + getDataFolder().toPath().relativize(backup));
            catalog.replace(getName(), new ContentLoader().load(getDataFolder().toPath().resolve("content")));
            int installed = ResourceInstaller.install(this);
            if (installed > 0) getLogger().info("已释放 " + installed + " 个手册 CE 文件；首次安装请执行 /ce reload all 并让玩家加载新资源包。");
        } catch (Exception error) {
            getLogger().severe("手册初始化失败：" + error.getMessage()); getServer().getPluginManager().disablePlugin(this); return;
        }
        ce = new CraftEngineBridge(this); ce.installPackHook(); views = new HandbookViews(this, catalog, ce);
        HandbookService service = new DefaultHandbookService(catalog, views);
        getServer().getServicesManager().register(HandbookService.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command(), "全局玩法手册", List.of("manual")));
        menu = new MainMenuBridge(this);
        if (getConfig().getBoolean("main-menu.enabled", true)) menu.register(service);
        getLogger().info("全局手册已启用：" + catalog.snapshot().categories().size() + " 个分类，"
                + catalog.snapshot().entries().size() + " 个条目。");
    }
    @Override public void onDisable() {
        if (menu != null) menu.close();
        if (views != null) views.clear();
        getServer().getServicesManager().unregisterAll(this);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { views.forget(event.getPlayer().getUniqueId()); }
    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != this) catalog.removeOwner(event.getPlugin().getName());
    }
    private com.mojang.brigadier.tree.LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> command() {
        return Commands.literal("handbook").requires(s -> s.getSender().hasPermission("handbookcore.use"))
                .executes(c -> withPlayer(c.getSource().getSender(), p -> views.home(p, 0)))
                .then(Commands.literal("category").then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(c -> withPlayer(c.getSource().getSender(), p -> views.category(p, id(StringArgumentType.getString(c, "id")), 0)))))
                .then(Commands.literal("open").then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(c -> withPlayer(c.getSource().getSender(), p -> views.entry(p, id(StringArgumentType.getString(c, "id")), 0)))))
                .then(Commands.literal("search").executes(c -> withPlayer(c.getSource().getSender(), views::searchDialog))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(c -> withPlayer(c.getSource().getSender(), p -> views.results(p, StringArgumentType.getString(c, "query"), 0)))))
                .then(Commands.literal("_nav").then(Commands.argument("nonce", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(c -> withPlayer(c.getSource().getSender(),
                                p -> views.navigate(p, StringArgumentType.getString(c, "nonce"), IntegerArgumentType.getInteger(c, "index")))))))
                .then(Commands.literal("reload").requires(s -> s.getSender().hasPermission("handbookcore.admin"))
                        .executes(c -> { reloadContent(c.getSource().getSender()); return 1; }))
                .then(Commands.literal("install-resources").requires(s -> s.getSender().hasPermission("handbookcore.admin"))
                        .executes(c -> {
                            try {
                                int count = ResourceInstaller.install(this);
                                c.getSource().getSender().sendMessage(Component.text("释放 " + count + " 个文件。请执行 /ce reload all 并重新加载服务器资源包。"));
                            } catch (IOException error) { c.getSource().getSender().sendMessage(Component.text("释放失败：" + error.getMessage())); }
                            return 1;
                        }))
                .then(Commands.literal("status").requires(s -> s.getSender().hasPermission("handbookcore.admin"))
                        .executes(c -> {
                            c.getSource().getSender().sendMessage(Component.text("HandbookCore " + getPluginMeta().getVersion()
                                    + " | 分类 " + catalog.snapshot().categories().size() + " | 条目 " + catalog.snapshot().entries().size()
                                    + " | CE " + ce.available() + " | 书页图片 " + ce.bookAvailable())); return 1;
                        })).build();
    }
    private void reloadContent(CommandSender sender) {
        getServer().getAsyncScheduler().runNow(this, ignored -> {
            try {
                var contribution = new ContentLoader().load(getDataFolder().toPath().resolve("content"));
                catalog.replace(getName(), contribution);
                getServer().getGlobalRegionScheduler().execute(this, () -> {
                    reloadConfig(); views.reloadSettings(); reply(sender, "手册已重载：" + contribution.entries().size() + " 个配置条目。");
                });
            } catch (Exception error) {
                reply(sender, "重载失败，保留原有内容：" + error.getMessage());
                getLogger().warning("手册重载失败：" + error.getMessage());
            }
        });
    }
    private void reply(CommandSender sender, String message) {
        if (sender instanceof Player player) player.getScheduler().run(this, ignored -> player.sendMessage(Component.text(message)), null);
        else sender.sendMessage(Component.text(message));
    }
    private int withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            try { action.accept(player); } catch (IllegalArgumentException error) { sender.sendMessage(Component.text(error.getMessage())); }
        } else sender.sendMessage(Component.text("请在游戏内打开手册。"));
        return 1;
    }
    private static NamespacedKey id(String value) {
        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null || !value.contains(":")) throw new IllegalArgumentException("请填写 namespace:id 形式的条目 id。");
        return key;
    }
}
