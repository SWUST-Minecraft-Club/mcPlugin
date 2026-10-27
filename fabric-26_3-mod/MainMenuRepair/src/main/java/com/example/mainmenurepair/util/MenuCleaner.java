package com.example.mainmenurepair.util;

import com.example.mainmenurepair.access.ScreenAccess;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.Component;

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

    /**
     * 把 ESC 菜单里的「主菜单」按钮换成"直接打开主菜单"的按钮，跳过中间的 dialog 页面。
     *
     * <p>做法：找到标签含 {@link #DIALOG_TITLE} 的按钮（服务端 dialog 的入口），
     * 在它原来的位置上放一个自己的按钮（点击发 {@code /cd}），再把原按钮隐藏。
     * 先加新按钮再隐藏旧的 —— 万一添加失败，原来的按钮还能用。
     *
     * <p>这条走的是 Fabric API 的界面事件（实测可靠）；Mixin 注入在这套环境里一直没生效，
     * 所以不再依赖它。
     */
    public static void replaceDialogButton(final Screen screen) {
        if (!(screen instanceof PauseScreen)) {
            return;
        }
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        for (final GuiEventListener child : new ArrayList<>(screen.children())) {
            if (!(child instanceof Button button)) {
                continue;
            }
            final String label = button.getMessage().getString();
            if (!label.contains(DIALOG_TITLE)) {
                continue;
            }
            final Button replacement = Button.builder(
                            Component.literal(label),
                            pressed -> client.player.connection.sendCommand("cd"))
                    .bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight())
                    .build();
            if (addWidget(screen, replacement)) {
                button.visible = false;
                button.active = false;
                StatusLog.append("已把「" + label + "」按钮换成直接打开主菜单（跳过中间 dialog）");
            }
            return;
        }
    }

    /** Screen#addRenderableWidget 是 protected，这里用反射调用 */
    private static boolean addWidget(final Screen screen, final GuiEventListener widget) {
        try {
            final Method method = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            method.setAccessible(true);
            method.invoke(screen, widget);
            return true;
        } catch (final Throwable throwable) {
            StatusLog.append("添加替换按钮失败：" + throwable);
            return false;
        }
    }

    /** 清理界面：暂停菜单去掉 4 个图标按钮和第三方警告；服务端 dialog 去掉右上角的警告按钮 */
    public static void clean(final Screen screen, final String source) {
        if (screen instanceof DialogScreen) {
            removeDialogWarning(screen, source);
            HandbookDialogLayout.center(screen);
            return;
        }
        if (!(screen instanceof PauseScreen)) {
            return;
        }

        final List<GuiEventListener> children = new ArrayList<>(screen.children());

        int icons = 0;
        int removed = 0;
        int iconTop = Integer.MAX_VALUE;
        int iconBottom = Integer.MIN_VALUE;
        final List<AbstractWidget> widgets = new ArrayList<>();
        for (final GuiEventListener child : children) {
            // SpriteIconButton 覆盖报告 Bug / 给予反馈 / 举报玩家，FriendsButton 也继承它
            if (child instanceof SpriteIconButton) {
                icons++;
                if (child instanceof AbstractWidget icon) {
                    iconTop = Math.min(iconTop, icon.getY());
                    iconBottom = Math.max(iconBottom, icon.getY() + icon.getHeight());
                    // 先隐藏：即使下面的移除失败，玩家也看不到它们
                    icon.visible = false;
                    icon.active = false;
                }
                try {
                    ((ScreenAccess) screen).mainmenuRepair$removeWidget(child);
                    removed++;
                } catch (final Throwable ignored) {
                    // 移除失败也没关系，已经隐藏了
                }
            } else if (child instanceof AbstractWidget widget) {
                widgets.add(widget);
            }
        }

        final int shifted = collapseIconRow(widgets, iconTop, iconBottom);

        int tooltips = 0;
        for (final GuiEventListener child : screen.children()) {
            // 剩下的按钮里只有服务器自定义选项按钮带提示（那句第三方警告）
            if (child instanceof AbstractWidget widget) {
                widget.setTooltip(null);
                tooltips++;
            }
        }

        StatusLog.append(source + "：控件 " + children.size() + " 个，图标 " + icons
                + " 个（移除 " + removed + "），空行上移 " + shifted + " 像素，清空提示 "
                + tooltips + " 个");
    }

    /** 原版暂停菜单网格每个单元格上下各有 4 像素内边距，相邻两行的正常间距是 8 */
    private static final int MENU_ROW_GAP = 8;

    /**
     * 收掉图标行空出来的高度。
     *
     * <p>原版 {@code GridLayout} 在图标被隐藏后仍为那一行保留高度（按钮高 + 上下内边距），
     * 中间的空白不会自己收掉。这里按实际控件位置算出当前间距，把这行下方的控件整体上移，
     * 让间距回到正常行距；重复执行时间距已经是正常值，会直接跳过。
     */
    private static int collapseIconRow(
            final List<AbstractWidget> widgets, final int iconTop, final int iconBottom) {
        if (iconTop == Integer.MAX_VALUE) {
            return 0;
        }
        AbstractWidget upper = null;
        AbstractWidget next = null;
        for (final AbstractWidget widget : widgets) {
            if (!widget.visible) {
                continue;
            }
            final int top = widget.getY();
            final int bottom = top + widget.getHeight();
            if (bottom <= iconTop && (upper == null || bottom > upper.getY() + upper.getHeight())) {
                upper = widget;
            }
            if (top >= iconBottom && (next == null || top < next.getY())) {
                next = widget;
            }
        }
        if (upper == null || next == null) {
            return 0;
        }
        final int gap = next.getY() - (upper.getY() + upper.getHeight());
        if (gap <= MENU_ROW_GAP) {
            return 0;
        }
        final int shift = gap - MENU_ROW_GAP;
        for (final AbstractWidget widget : widgets) {
            if (widget.getY() >= iconBottom) {
                widget.setY(widget.getY() - shift);
            }
        }
        return shift;
    }

    /**
     * 去掉服务端 dialog 标题右侧的「自定义屏幕」警告按钮。
     *
     * <p>{@code DialogScreen.init()} 会无条件创建一个 {@link ImageButton} 并挂进标题行，
     * 按钮本身和它打开的说明页都由客户端提供，服务端控制不了。这里按提示文本识别
     * （{@code menu.custom_screen_info.*}，不受客户端语言影响）并隐藏。
     *
     * <p>布局在 {@code init()} 返回前就按原按钮宽度排好了，只把按钮尺寸归零不会触发重排，
     * 标题仍会偏左；所以隐藏后再把标题控件按屏幕宽度摆正。窗口缩放会重新初始化界面，
     * 这条清理会再执行一次。
     */
    private static void removeDialogWarning(final Screen screen, final String source) {
        final String warningText =
                Component.translatable("menu.custom_screen_info.button_narration").getString();
        final String titleText = screen.getTitle().getString();
        StringWidget title = null;
        int hidden = 0;
        for (final GuiEventListener child : new ArrayList<>(screen.children())) {
            if (child instanceof ImageButton button) {
                if (!button.getMessage().getString().equals(warningText)) {
                    continue;
                }
                button.visible = false;
                button.active = false;
                button.setWidth(0);
                button.setHeight(0);
                hidden++;
            } else if (child instanceof StringWidget widget
                    && widget.getMessage().getString().equals(titleText)) {
                title = widget;
            }
        }
        if (title != null) {
            title.setX((screen.width - title.getWidth()) / 2);
        }
        StatusLog.append(source + "：dialog 警告按钮 " + hidden + " 个"
                + (title == null ? "" : "，标题已居中"));
    }
}
