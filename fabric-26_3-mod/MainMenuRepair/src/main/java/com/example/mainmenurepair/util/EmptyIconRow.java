package com.example.mainmenurepair.util;

import java.util.function.Consumer;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;

/**
 * 一个会“吞掉”所有子控件的横向布局：任何 addChild 都被忽略，因此它永远是空的。
 *
 * <p>用来替换暂停菜单右下角那一行图标（报告 Bug / 给予反馈 / 好友 / 举报玩家）：
 * 这四个按钮都是客户端在 {@code PauseScreen.createPauseMenu()} 里加进同一个
 * {@code LinearLayout.horizontal()} 布局的，换成这个实现后它们就不会被创建。
 *
 * <p>三个 addChild 重载都要覆盖，避免不同版本换用别的重载。
 */
public final class EmptyIconRow extends LinearLayout {

    public EmptyIconRow() {
        super(0, 0, Orientation.HORIZONTAL);
    }

    @Override
    public <T extends LayoutElement> T addChild(final T child) {
        return child;
    }

    @Override
    public <T extends LayoutElement> T addChild(final T child, final LayoutSettings settings) {
        return child;
    }

    @Override
    public <T extends LayoutElement> T addChild(final T child, final Consumer<LayoutSettings> settings) {
        return child;
    }
}
