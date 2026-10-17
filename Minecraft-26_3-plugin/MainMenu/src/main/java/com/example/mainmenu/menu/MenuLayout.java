package com.example.mainmenu.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 6×9 主菜单的布局规则（与需求一一对应）。
 *
 * <pre>
 * 第1行   [ ][钟][ ][ ][ ][ ][ ][ ][ ]
 * 第2行   玻璃板 ×9                      ← 非固定功能位，可被功能插件占用
 * 第3行   [ ][功能区 ×7 ][ ]              ┐
 * 第4行   [←][功能区 ×7 ][→]              ├ 功能区：第 3~5 行、第 2~8 列，共 21 格，可翻页
 * 第5行   [ ][功能区 ×7 ][ ]              ┘
 * 第6行   玻璃板 ×9                      ← 非固定功能位，可被功能插件占用
 * </pre>
 *
 * 固定功能位（不可被功能插件占用）：时钟、上一页、下一页。
 * 其余空位默认放白色染色玻璃板，属于非固定功能位，功能插件可以用自定义位置占用。
 */
public final class MenuLayout {

    /** 行数 */
    public static final int ROWS = 6;
    /** 列数 */
    public static final int COLUMNS = 9;
    /** 总格子数 */
    public static final int SIZE = ROWS * COLUMNS;

    /** 第 1 行第 2 格：当前时间 */
    public static final int CLOCK_SLOT = 1;
    /** 第 4 行第 1 格：上一页 */
    public static final int PREVIOUS_PAGE_SLOT = 3 * COLUMNS;
    /** 第 4 行第 9 格：下一页 */
    public static final int NEXT_PAGE_SLOT = 3 * COLUMNS + (COLUMNS - 1);

    /** 固定功能位：不可被功能插件占用，冲突时功能会被改排到功能区 */
    public static final Set<Integer> FIXED_SLOTS =
            Set.of(CLOCK_SLOT, PREVIOUS_PAGE_SLOT, NEXT_PAGE_SLOT);

    /** 功能区：第 3~5 行、第 2~8 列 */
    public static final List<Integer> FUNCTION_AREA = buildFunctionArea();

    /** 功能区每页容量 */
    public static final int PAGE_SIZE = FUNCTION_AREA.size();

    private MenuLayout() {
    }

    private static List<Integer> buildFunctionArea() {
        final List<Integer> slots = new ArrayList<>(3 * 7);
        for (int row = 2; row <= 4; row++) {          // 0 基：第 3~5 行
            for (int column = 1; column <= 7; column++) {   // 0 基：第 2~8 列
                slots.add(row * COLUMNS + column);
            }
        }
        return List.copyOf(slots);
    }

    /** 是否固定功能位 */
    public static boolean isFixed(final int slot) {
        return FIXED_SLOTS.contains(slot);
    }

    /** 是否功能区格子 */
    public static boolean isFunctionArea(final int slot) {
        return FUNCTION_AREA.contains(slot);
    }

    /** 是否非固定功能位（空位，可被自定义位置占用） */
    public static boolean isFreeSlot(final int slot) {
        return slot >= 0 && slot < SIZE && !isFixed(slot) && !isFunctionArea(slot);
    }

    /** 0 基行号（0~5） */
    public static int row(final int slot) {
        return slot / COLUMNS;
    }

    /** 0 基列号（0~8） */
    public static int column(final int slot) {
        return slot % COLUMNS;
    }

    /** 人类可读的位置描述，例如“第 4 行第 1 格” */
    public static String describe(final int slot) {
        return "第 " + (row(slot) + 1) + " 行第 " + (column(slot) + 1) + " 格（槽位 " + slot + "）";
    }
}
