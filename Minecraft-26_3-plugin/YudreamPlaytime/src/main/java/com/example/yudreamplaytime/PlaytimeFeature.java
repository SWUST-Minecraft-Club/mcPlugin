package com.example.yudreamplaytime;

import com.example.mainmenu.api.MenuFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * 主菜单里的「我的在线时长」功能项。
 *
 * <p>按查看者渲染（覆写 {@code icon(viewer)} / {@code description(viewer)}）：
 * 图标是查看者自己的头，悬停显示玩家名、在线时长、挂机时长；点击无效（{@code clickable()} 为 false）。
 *
 * <p>数字取自本地时长台账，口径与 YuDream Admin 后端一致（含当前未结算区间；AFK 单独统计），
 * 所以后端挂了这里照样显示。
 */
public final class PlaytimeFeature implements MenuFeature {

    private final PlaytimeStats stats;
    private final int order;

    public PlaytimeFeature(final PlaytimeStats stats, final int order) {
        this.stats = stats;
        this.order = order;
    }

    @Override
    public String id() {
        return "yudreamplaytime:my-playtime";
    }

    @Override
    public String owner() {
        return "YudreamPlaytime";
    }

    /** 静态图标：查看者不可用时（例如后台校验、管理界面拿不到玩家）的兜底。 */
    @Override
    public ItemStack icon() {
        return head(null);
    }

    @Override
    public ItemStack icon(final Player viewer) {
        return head(viewer);
    }

    @Override
    public List<Component> description() {
        final List<Component> lines = new ArrayList<>(1);
        lines.add(plain("查看你自己的在线时长与挂机时长", NamedTextColor.GRAY));
        return lines;
    }

    @Override
    public List<Component> description(final Player viewer) {
        final long now = System.currentTimeMillis();
        final PlaytimeStats.Record record = stats.find(viewer.getUniqueId());
        final List<Component> lines = new ArrayList<>(3);
        lines.add(label("玩家：", viewer.getName()));
        if (record == null) {
            lines.add(label("在线时长：", DurationFormat.format(0L)));
            lines.add(label("挂机时长：", DurationFormat.format(0L)));
        } else {
            lines.add(label("在线时长：", DurationFormat.format(record.onlineMillisAt(now))));
            lines.add(label("挂机时长：", DurationFormat.format(record.afkMillisAt(now))));
        }
        return lines;
    }

    @Override
    public boolean clickable() {
        return false;
    }

    /** 点击无效：主菜单在 {@code clickable()} 为 false 时不会调用它。 */
    @Override
    public void onClick(final Player player, final ClickType clickType) {
        // 展示型功能，不做任何事
    }

    /** 不占用固定位置，排进功能区（按 {@code order} 定序）。 */
    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return order;
    }

    private ItemStack head(final Player viewer) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            if (viewer != null) {
                meta.setOwningPlayer(viewer);
                meta.displayName(plain(viewer.getName(), NamedTextColor.WHITE));
            } else {
                meta.displayName(plain("我的在线时长", NamedTextColor.WHITE));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component label(final String label, final String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static Component plain(final String text, final NamedTextColor color) {
        return Component.text(text, color)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
