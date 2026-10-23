package com.example.mainmenu.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 功能插件管理界面的布局。
 *
 * <pre>
 * 第1行   [添加占位符][黑玻璃板][添加自定义][黑玻璃板 ×6]
 * 第2行   [左栏1][左栏2][左栏3][左栏4][黑][右栏1][右栏2][右栏3][右栏4]
 * 第3行   同上
 * 第4行   同上
 * 第5行   同上
 * 第6行   [左上一页][黑][黑][左下一页][返回][右上一页][黑][黑][右下一页]
 * </pre>
 *
 * 左栏 = 非固定位置功能插件（功能区里的）与占位符，右栏 = 固定位置功能插件。
 */
public final class AdminLayout {

    /** 左栏：第 2~5 行、第 1~4 列 */
    public static final List<Integer> LEFT_SLOTS = columnRange(2, 5, 1, 4);
    /** 右栏：第 2~5 行、第 6~9 列 */
    public static final List<Integer> RIGHT_SLOTS = columnRange(2, 5, 6, 9);

    /** 第 1 行第 1 格：添加功能占位符 */
    public static final int ADD_PLACEHOLDER = 0;
    /** 第 1 行第 3 格：添加自定义插件功能 */
    public static final int ADD_CUSTOM = 2;

    /** 第 6 行第 1 格：左栏上一页 */
    public static final int LEFT_PREVIOUS = 5 * MenuLayout.COLUMNS;
    /** 第 6 行第 4 格：左栏下一页 */
    public static final int LEFT_NEXT = 5 * MenuLayout.COLUMNS + 3;
    /** 第 6 行第 5 格：返回（屏障） */
    public static final int BACK = MenuLayout.ADMIN_SLOT;
    /** 第 6 行第 6 格：右栏上一页 */
    public static final int RIGHT_PREVIOUS = 5 * MenuLayout.COLUMNS + 5;
    /** 第 6 行第 9 格：右栏下一页 */
    public static final int RIGHT_NEXT = 5 * MenuLayout.COLUMNS + 8;

    /** 每栏每页可显示的功能数 */
    public static final int PAGE_SIZE = LEFT_SLOTS.size();

    /** 属性页：第 3 行第 3 格（显示 / 隐藏） */
    public static final int ATTR_TOGGLE = 2 * MenuLayout.COLUMNS + 2;
    /** 属性页：第 3 行第 5 格（非固定位=排序值，固定位=更改位置） */
    public static final int ATTR_MIDDLE = 2 * MenuLayout.COLUMNS + 4;
    /** 属性页：第 3 行第 7 格（非固定位=转为固定位置） */
    public static final int ATTR_RIGHT = 2 * MenuLayout.COLUMNS + 6;

    private AdminLayout() {
    }

    private static List<Integer> columnRange(final int firstRow, final int lastRow,
                                             final int firstColumn, final int lastColumn) {
        final List<Integer> slots = new ArrayList<>();
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                slots.add((row - 1) * MenuLayout.COLUMNS + (column - 1));
            }
        }
        return List.copyOf(slots);
    }

    /** 管理页的分隔玻璃板：第 1 行、第 6 行、第 5 列（按钮占用的格子除外） */
    public static List<Integer> dividerSlots() {
        final Set<Integer> buttons = Set.of(
                ADD_PLACEHOLDER, ADD_CUSTOM, LEFT_PREVIOUS, LEFT_NEXT, BACK,
                RIGHT_PREVIOUS, RIGHT_NEXT);
        final List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            final boolean firstRow = slot < MenuLayout.COLUMNS;
            final boolean lastRow = slot >= 5 * MenuLayout.COLUMNS;
            final boolean middleColumn = slot % MenuLayout.COLUMNS == 4;
            if ((firstRow || lastRow || middleColumn) && !buttons.contains(slot)) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    /** 位置选择界面里可点击设置的位置：功能区、翻页箭头、管理入口以外的格子 */
    public static boolean isSelectable(final int slot) {
        if (MenuLayout.isFunctionArea(slot)) {
            return false;
        }
        return slot != MenuLayout.PREVIOUS_PAGE_SLOT
                && slot != MenuLayout.NEXT_PAGE_SLOT
                && slot != MenuLayout.ADMIN_SLOT;
    }
}
