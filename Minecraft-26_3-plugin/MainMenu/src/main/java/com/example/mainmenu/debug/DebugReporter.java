package com.example.mainmenu.debug;

import com.example.mainmenu.MainMenuBootstrap;
import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.internal.PlaceholderFeature;
import com.example.mainmenu.menu.MenuLayout;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.tag.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;

/**
 * {@code /cd debug}：输出布局、功能位置、ESC 按钮状态与配置文本检查结果。
 * 排查“ESC 菜单没出现按钮”“功能跑到功能区了”这类问题时先看这里。
 */
public final class DebugReporter {

    /** 会被渲染成文本的配置项，用于自检 MiniMessage 是否可解析 */
    private static final List<String> TEXT_KEYS = List.of(
            "menu.title",
            "menu.filler.name",
            "menu.paging.previous-name", "menu.paging.next-name", "menu.paging.page-line",
            "menu.paging.first-page", "menu.paging.last-page",
            "menu.feature.conflict-hint",
            "messages.no-permission", "messages.players-only", "messages.reloaded");

    private final MainMenuPlugin plugin;

    public DebugReporter(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    public void report(final CommandSender sender) {
        header(sender, "MainMenu 调试信息");
        line(sender, "插件", plugin.getPluginMeta().getName() + " v" + plugin.getPluginMeta().getVersion()
                + "（API " + plugin.getPluginMeta().getAPIVersion() + "）");
        line(sender, "服务端", plugin.getServer().getMinecraftVersion() + " / " + plugin.getServer().getVersion());
        reportDatapack(sender);
        reportDialogTag(sender);
        reportFeatures(sender);
        reportLayout(sender, 1);
        reportConfig(sender);
    }

    private void reportDatapack(final CommandSender sender) {
        final DatapackManager manager = plugin.getServer().getDatapackManager();
        final List<String> found = new ArrayList<>();
        for (final Datapack pack : manager.getPacks()) {
            if (pack.getName().toLowerCase(Locale.ROOT).contains(MainMenuBootstrap.PACK_NAME)) {
                found.add(pack.getName() + (pack.isEnabled() ? "（已启用）" : "（未启用）")
                        + " 来源=" + pack.getSource());
            }
        }
        line(sender, "内置数据包", found.isEmpty() ? "未发现（ESC 菜单按钮不可用）" : String.join("，", found));
    }

    private void reportDialogTag(final CommandSender sender) {
        try {
            final Registry<Dialog> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG);
            final Tag<Dialog> tag = registry.getTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS);
            if (tag == null) {
                line(sender, "ESC 菜单按钮", "服务端没有 pause_screen_additions 标签（版本不支持）");
                return;
            }
            final boolean contains = tag.contains(MainMenuBootstrap.dialogKey());
            line(sender, "ESC 菜单按钮", contains
                    ? "已就绪：pause_screen_additions 包含 mainmenu:main_menu（标签共 " + tag.size() + " 项）"
                    : "未生效：标签里没有 mainmenu:main_menu（标签共 " + tag.size() + " 项，检查数据包是否启用）");
        } catch (final Exception exception) {
            line(sender, "ESC 菜单按钮", "读取注册表失败：" + exception.getMessage());
        }
    }

    private void reportFeatures(final CommandSender sender) {
        final List<MenuFeature> features = plugin.features().features();
        line(sender, "功能区", features.size() + " 个功能，共 " + plugin.features().pageCount()
                + " 页（每页 " + plugin.features().pageSize() + " 格）");
        line(sender, "功能分类", "固定位置 " + plugin.features().fixedFeatures().size() + " 个；非固定位置 "
                + plugin.features().functionAreaFeatures().size() + " 个（都含被隐藏的）");
        if (features.isEmpty()) {
            sender.sendMessage(Component.text("  （还没有插件注册功能；功能插件需 depend MainMenu）", NamedTextColor.GRAY));
        }
        for (final FeaturePlacement placement : plugin.features().placements()) {
            final StringBuilder text = new StringBuilder("  ")
                    .append(placement.id())
                    .append(" → ").append(MenuLayout.describe(placement.slot()))
                    .append(placement.customPosition() ? " 自定义位置" : " 第 " + placement.page() + " 页");
            if (placement.movedToFunctionArea()) {
                text.append("（自定义位置冲突，已自动归到功能区）");
            }
            sender.sendMessage(Component.text(text.toString(), NamedTextColor.GRAY));
        }
    }

    private void reportLayout(final CommandSender sender, final int page) {
        line(sender, "布局（第 " + page + " 页）", "");
        for (int row = 0; row < MenuLayout.ROWS; row++) {
            final StringBuilder text = new StringBuilder("  第 ").append(row + 1).append(" 行: ");
            for (int column = 0; column < MenuLayout.COLUMNS; column++) {
                final int slot = row * MenuLayout.COLUMNS + column;
                text.append('[').append(slot).append(']').append(labelOf(slot, page)).append(' ');
            }
            sender.sendMessage(Component.text(text.toString(), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("  图例：* = 功能插件自定义位置占用", NamedTextColor.DARK_GRAY));
    }

    private String labelOf(final int slot, final int page) {
        if (slot == MenuLayout.PREVIOUS_PAGE_SLOT) {
            return "上一页";
        }
        if (slot == MenuLayout.NEXT_PAGE_SLOT) {
            return "下一页";
        }
        if (slot == MenuLayout.ADMIN_SLOT) {
            return "管理入口";
        }
        final Optional<MenuFeature> feature = plugin.features().featureAt(slot, page);
        if (feature.isPresent()) {
            if (feature.get() instanceof PlaceholderFeature) {
                return "占位符";
            }
            final String id = feature.get().id();
            return MenuLayout.isFunctionArea(slot) ? id : id + "*";
        }
        return MenuLayout.isFunctionArea(slot) ? "空" : "玻璃板";
    }

    private void reportConfig(final CommandSender sender) {
        final List<String> problems = new ArrayList<>();
        for (final String key : TEXT_KEYS) {
            try {
                plugin.mini(key);
            } catch (final RuntimeException exception) {
                problems.add(key + "（" + exception.getMessage() + "）");
            }
        }
        line(sender, "配置文本检查", problems.isEmpty()
                ? TEXT_KEYS.size() + " 项均可解析"
                : "有问题：" + String.join("；", problems));
    }

    // ------------------------------------------------------------------

    private static void header(final CommandSender sender, final String text) {
        sender.sendMessage(Component.text("=== " + text + " ===", NamedTextColor.AQUA));
    }

    private static void line(final CommandSender sender, final String label, final String value) {
        sender.sendMessage(Component.text(label + "：", NamedTextColor.YELLOW)
                .append(Component.text(value, NamedTextColor.WHITE)));
    }
}
