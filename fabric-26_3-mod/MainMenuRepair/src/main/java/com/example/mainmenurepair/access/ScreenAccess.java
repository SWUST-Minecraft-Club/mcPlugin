package com.example.mainmenurepair.access;

import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * 由 {@code ScreenInvokerMixin} 混入 {@link net.minecraft.client.gui.screens.Screen}，
 * 用来调用 protected 的 {@code Screen#removeWidget(GuiEventListener)}。
 *
 * <p>注意：目标 {@code Screen} 是**类**，所以 mixin 必须写成类（并实现本接口），
 * 不能写成接口形式的 mixin —— 否则 Mixin 会报
 * “@Mixin target type mismatch: ... is not an interface”。
 */
public interface ScreenAccess {

    void mainmenuRepair$removeWidget(GuiEventListener widget);
}
