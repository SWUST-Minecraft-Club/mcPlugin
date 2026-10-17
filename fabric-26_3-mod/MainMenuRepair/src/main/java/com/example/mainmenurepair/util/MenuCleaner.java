package com.example.mainmenurepair.util;

import com.example.mainmenurepair.access.ScreenAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

/**
 * 暂停菜单清理逻辑。
 *
 * <p><b>故意放在普通类里</b>：mixin 类中的 {@code private static} 辅助方法和 lambda 不会被合并进目标类，
 * 在 mixin 里调用它们会在运行时抛 NoSuchMethodError（已经踩过一次）。放到普通类里就没这个问题。
 */
public final class MenuCleaner {

    /** 服务端注册的 dialog 标题，用来识别"点 ESC 菜单里那个按钮时打开的中间页面" */
    public static final String DIALOG_TITLE = "主菜单";

    private MenuCleaner() {
    }

    /**
     * 是否是主菜单插件注册的那个 dialog 界面（ESC → 主菜单 会先打开它）。
     *
     * <p>判断方式：类型是 {@link DialogScreen}，且标题里含服务端下发的“主菜单”。
     * 标题文本是服务端下发的字面量，不受客户端语言影响。
     */
    public static boolean isOurDialog(final Screen screen) {
        if (!(screen instanceof DialogScreen)) {
            return false;
        }
        return screen.getTitle().getString().contains(DIALOG_TITLE);
    }

    /** 对暂停菜单做清理：隐藏并移除 4 个图标按钮、清空按钮上的提示 */
    public static void clean(final Screen screen, final String source) {
        if (!(screen instanceof PauseScreen)) {
            return;
        }

        final List<GuiEventListener> children = new ArrayList<>(screen.children());

        int icons = 0;
        int removed = 0;
        for (final GuiEventListener child : children) {
            // SpriteIconButton 覆盖报告 Bug / 给予反馈 / 举报玩家，FriendsButton 也继承它
            if (!(child instanceof SpriteIconButton)) {
                continue;
            }
            icons++;
            if (child instanceof AbstractWidget widget) {
                // 先隐藏：即使下面的移除失败，玩家也看不到它们
                widget.visible = false;
                widget.active = false;
            }
            try {
                ((ScreenAccess) screen).mainmenuRepair$removeWidget(child);
                removed++;
            } catch (final Throwable ignored) {
                // 移除失败也没关系，已经隐藏了
            }
        }

        int tooltips = 0;
        for (final GuiEventListener child : screen.children()) {
            // 剩下的按钮里只有服务器自定义选项按钮带提示（那句第三方警告）
            if (child instanceof AbstractWidget widget) {
                widget.setTooltip(null);
                tooltips++;
            }
        }

        StatusLog.append(source + "：控件 " + children.size() + " 个，图标 " + icons
                + " 个（移除 " + removed + "），清空提示 " + tooltips + " 个");
    }
}
