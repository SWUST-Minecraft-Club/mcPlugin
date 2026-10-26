package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import java.lang.reflect.InvocationTargetException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Optional display renderer; this boundary has no ArcMenu types. */
public interface DisplayMenus {

    boolean openHome(Player player);

    boolean openManage(Player player);

    boolean openEconomy(Player player);

    void reload(CommandSender sender);

    void shutdown();

    static DisplayMenus create(final MainMenuPlugin plugin) {
        if (!plugin.getConfig().getBoolean("menu.arcmenu.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("ArcMenu") == null) {
            return null;
        }
        try {
            // Resolve the optional API only after checking that its provider exists.
            return (DisplayMenus) Class.forName("com.example.mainmenu.arcmenu.ArcMenuBridge")
                    .getMethod("create", MainMenuPlugin.class).invoke(null, plugin);
        } catch (final ReflectiveOperationException | LinkageError e) {
            final Throwable cause = e instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : e;
            plugin.getLogger().warning("ArcMenu 接入失败，使用背包菜单：" + cause);
            return null;
        }
    }
}
