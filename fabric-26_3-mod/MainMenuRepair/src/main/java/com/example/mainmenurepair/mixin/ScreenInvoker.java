package com.example.mainmenurepair.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Screen#removeWidget} —— 它是 protected，混入类里不能直接调用，用 @Invoker 生成一个公开入口。
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("removeWidget")
    void mainmenuRepair$removeWidget(GuiEventListener widget);
}
