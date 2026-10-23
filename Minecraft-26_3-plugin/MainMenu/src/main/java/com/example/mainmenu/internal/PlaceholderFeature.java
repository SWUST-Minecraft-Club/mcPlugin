package com.example.mainmenu.internal;

import com.example.mainmenu.api.MenuFeature;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 管理界面专用的功能占位符。
 *
 * <p>它不是功能插件注册的功能项，而是管理员在管理页手动添加的“预留位置”：
 * <ul>
 *     <li>参与功能区的排序与分页，管理页左栏按排序值显示；</li>
 *     <li>主菜单渲染时跳过，因此玩家侧看不到红色玻璃板（该格保持空位）；</li>
 *     <li>管理页点击后只提供排序值调整与删除，不允许隐藏 / 固定位置等操作。</li>
 * </ul>
 */
public final class PlaceholderFeature implements MenuFeature {

    private final String id;

    public PlaceholderFeature(final String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String owner() {
        return "占位符";
    }

    @Override
    public ItemStack icon() {
        final ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("功能占位符", NamedTextColor.WHITE)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("占位符：仅管理界面可见", NamedTextColor.GRAY)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE),
                Component.text("主菜单不显示该格", NamedTextColor.DARK_GRAY)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    @Override
    public boolean clickable() {
        return false;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        // 管理界面由 AdminService 处理，主菜单不渲染也不响应
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return 0;
    }
}
