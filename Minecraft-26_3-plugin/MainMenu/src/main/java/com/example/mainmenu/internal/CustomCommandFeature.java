package com.example.mainmenu.internal;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 管理员在管理页创建、由 {@code custom-features.yml} 配置的自定义功能。
 *
 * <p>它和普通功能插件注册的功能走同一套管理逻辑：显示 / 隐藏、排序、固定位置都由
 * {@link FeatureStateStore} 管理。配置文件只负责外观与点击行为：
 * <ul>
 *     <li>{@code name}：显示名（纯文本）；</li>
 *     <li>{@code icon}：Bukkit 材质名，默认 {@code PAPER}；</li>
 *     <li>{@code command}：点击执行的指令（不带 {@code /}；留空则点击无效）；</li>
 *     <li>{@code description}：悬停文本，MiniMessage 列表；</li>
 *     <li>{@code order}：默认排序值（管理页可覆盖）。</li>
 * </ul>
 */
public final class CustomCommandFeature implements MenuFeature {

    private final MainMenuPlugin plugin;
    private final String id;
    private final String name;
    private final Material icon;
    private final String command;
    private final List<String> description;
    private final int order;

    public CustomCommandFeature(final MainMenuPlugin plugin, final String id, final String name,
                                final String iconName, final String command,
                                final List<String> description, final int order) {
        this.plugin = plugin;
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name.trim();
        this.command = command == null ? "" : command.trim();

        Material material = null;
        try {
            material = Material.matchMaterial(iconName == null ? "" : iconName.trim());
        } catch (RuntimeException ignored) {
            // 非法材质名按默认纸处理
        }
        if (material == null || material.isAir()) {
            material = Material.PAPER;
            if (iconName != null && !iconName.isBlank()) {
                plugin.getLogger().warning("自定义功能 " + id + " 的 icon 无效：" + iconName
                        + "，已回退为 PAPER");
            }
        }
        this.icon = material;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.order = order;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String owner() {
        return "自定义";
    }

    @Override
    public ItemStack icon() {
        final ItemStack item = new ItemStack(icon);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<Component> description() {
        final List<Component> lines = new ArrayList<>();
        final List<String> raw = description.isEmpty()
                ? List.of("<gray>自定义功能</gray>")
                : description;
        for (final String line : raw) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                lines.add(plugin.miniRaw(line));
            } catch (RuntimeException ex) {
                lines.add(Component.text(line, NamedTextColor.GRAY)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        if (command.isBlank()) {
            lines.add(Component.text("未配置点击指令（编辑 custom-features.yml 后 /cd reload）",
                            NamedTextColor.RED)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return lines;
    }

    @Override
    public boolean clickable() {
        return !command.isBlank();
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (command.isBlank()) {
            return;
        }
        final String execute = command.startsWith("/") ? command.substring(1) : command;
        if (execute.isBlank()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(player, execute);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "自定义功能 " + id + " 执行指令失败：" + execute, ex);
        }
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return order;
    }
}
