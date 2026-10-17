package com.example.mainmenu.api;

import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * 主菜单里的一个功能项。由功能插件实现并通过 {@link MenuFeatureRegistry#register(MenuFeature)} 注册。
 *
 * <p>规范要点：
 * <ul>
 *     <li>{@link #icon()} 决定用什么物品显示；每次渲染都会调用，需要动态文本时可在实现里自行缓存。</li>
 *     <li>{@link #description()} 是鼠标悬停时显示的文本，位置冲突时主菜单会在最后追加一行提示。</li>
 *     <li>{@link #clickable()} 为 {@code false} 时点击无效（只作为展示）；为 {@code true} 时必须实现
 *         {@link #onClick(Player, ClickType)}。</li>
 *     <li>{@link #preferredSlot()} 可自定义显示位置（0~53，且必须是功能区以外的格子）；不设置则按注册顺序
 *         排进功能区（第 3~5 行、第 2~8 列），功能区内按每页 21 个分页。</li>
 * </ul>
 *
 * @see MenuFeatureRegistry
 * @see MainMenuApi
 */
public interface MenuFeature {

    /** 唯一标识，建议 {@code "<插件名>:<功能名>"}，重复注册会被拒绝 */
    String id();

    /** 注册方插件名，用于日志与 /cd debug 排查 */
    String owner();

    /** 菜单里显示用的物品（主菜单会复制它，可以安全复用同一个实例） */
    ItemStack icon();

    /** 鼠标悬停显示的文本行 */
    List<Component> description();

    /** 点击是否有效 */
    boolean clickable();

    /** 点击有效时的处理逻辑；{@link #clickable()} 为 false 时不会被调用 */
    void onClick(Player player, ClickType clickType);

    /** 自定义显示位置（0~53）；{@link OptionalInt#empty()} 表示进功能区按顺序排 */
    OptionalInt preferredSlot();

    /** 创建一个功能项构建器 */
    static Builder builder(final String id) {
        return new com.example.mainmenu.internal.MenuFeatureBuilder(id);
    }

    /** 功能项构建器：物品、悬停文本、点击行为、自定义位置 */
    interface Builder {

        /** 注册方插件名；不设置时取 id 中 {@code :} 之前的部分 */
        Builder owner(String owner);

        /** 用物品本身作为图标（材质、附魔、自定义模型等都保留） */
        Builder icon(ItemStack icon);

        /** 用材质作为图标 */
        Builder icon(Material material);

        /** 物品显示名（MiniMessage 文本） */
        Builder name(String miniMessage);

        /** 物品显示名 */
        Builder name(Component name);

        /** 追加一行悬停文本（MiniMessage 文本） */
        Builder line(String miniMessage);

        /** 追加一行悬停文本 */
        Builder line(Component line);

        /** 设置全部悬停文本 */
        Builder lines(List<Component> lines);

        /** 点击是否有效，默认 false */
        Builder clickable(boolean clickable);

        /** 点击处理逻辑（设置后自动视为可点击） */
        Builder onClick(java.util.function.BiConsumer<Player, ClickType> handler);

        /** 自定义显示位置（0~53，功能区以外的格子） */
        Builder preferredSlot(int slot);

        MenuFeature build();
    }
}
