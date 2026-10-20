package com.example.realtime;

import com.example.mainmenu.api.MenuFeature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 主菜单里的「当前时间」功能项。
 *
 * <p>直接实现 {@link MenuFeature}（而不是用 builder）：{@code description()} 在每次渲染时都会被调用，
 * 主菜单打开期间会按 {@code menu.refresh-seconds} 周期性重绘，所以时间会自动跟着走。
 */
public final class TimeFeature implements MenuFeature {

    /** 主菜单第 1 行第 2 格（推荐给时间显示的位置） */
    private static final int SLOT = 1;

    private final RealTimePlugin plugin;
    private final ItemStack icon;
    private final DateTimeFormatter formatter;

    public TimeFeature(final RealTimePlugin plugin) {
        this.plugin = plugin;

        final Material configured = Material.matchMaterial(plugin.string("time.material", "CLOCK"));
        final Material material = configured == null || configured.isAir() ? Material.CLOCK : configured;
        this.icon = new ItemStack(material);
        final ItemMeta meta = this.icon.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.mini("time.name").decorationIfAbsent(
                    TextDecoration.ITALIC, TextDecoration.State.FALSE));
            this.icon.setItemMeta(meta);
        }

        this.formatter = DateTimeFormatter
                .ofPattern(plugin.string("time.format", "yyyy-MM-dd HH:mm:ss"))
                .withZone(ZoneOffset.ofHours(plugin.getConfig().getInt("time.zone-offset-hours", 8)));
    }

    @Override
    public String id() {
        return "realtime:clock";
    }

    @Override
    public String owner() {
        return plugin.getPluginMeta().getName();
    }

    @Override
    public ItemStack icon() {
        return icon.clone();
    }

    /** 每次渲染都会调用，所以显示的是"此刻"的时间 */
    @Override
    public List<Component> description() {
        final List<Component> lore = new ArrayList<>(1);
        lore.add(plugin.miniRaw(plugin.string("time.line", "<gray>当前时间：<white>{time}</white></gray>")
                .replace("{time}", formatter.format(Instant.now()))));
        return lore;
    }

    @Override
    public boolean clickable() {
        return false;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        // 展示型功能，点击无效
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.of(SLOT);
    }
}
