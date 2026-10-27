package com.example.handbookcore.hook;

import com.example.handbookcore.api.HandbookService;
import java.lang.reflect.Proxy;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** 全反射软依赖；安装或移除主菜单不会阻止手册独立运行。 */
public final class MainMenuBridge {
    private static final String ID = "handbookcore:handbook";
    private final Plugin plugin;
    private Object registry;
    private Class<?> registryType;
    public MainMenuBridge(Plugin plugin) { this.plugin = plugin; }
    public void register(HandbookService service) {
        Plugin menu = plugin.getServer().getPluginManager().getPlugin("MainMenu");
        if (menu == null || !menu.isEnabled()) return;
        try {
            ClassLoader loader = menu.getClass().getClassLoader();
            Class<?> feature = Class.forName("com.example.mainmenu.api.MenuFeature", true, loader);
            registryType = Class.forName("com.example.mainmenu.api.MenuFeatureRegistry", true, loader);
            registry = Class.forName("com.example.mainmenu.api.MainMenuApi", true, loader).getMethod("registry").invoke(null);
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{feature}, (self, method, args) -> switch (method.getName()) {
                case "id" -> ID;
                case "owner" -> plugin.getName();
                case "icon" -> {
                    ItemStack icon = new ItemStack(Material.BOOK);
                    icon.editMeta(meta -> meta.displayName(Component.text("服务器手册")));
                    yield icon;
                }
                case "description" -> List.of(Component.text("服务器手册"), Component.text("玩法攻略与服务器更新记录"));
                case "clickable" -> true;
                case "preferredSlot" -> java.util.OptionalInt.empty();
                case "order" -> 20;
                case "onClick" -> { service.openHome((Player) args[0]); yield null; }
                case "toString" -> ID;
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(self, method, args) : null;
            });
            registryType.getMethod("register", feature).invoke(registry, proxy);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().warning("手册主菜单入口未注册：" + error.getClass().getSimpleName());
        }
    }
    public void close() {
        if (registry == null) return;
        try { registryType.getMethod("unregister", String.class).invoke(registry, ID); }
        catch (ReflectiveOperationException | LinkageError ignored) { }
        registry = null;
    }
}
