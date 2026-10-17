package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.access.ScreenAccess;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把 protected 的 {@code Screen#removeWidget(GuiEventListener)} 暴露成 {@link ScreenAccess}。
 */
@Mixin(Screen.class)
public abstract class ScreenInvokerMixin implements ScreenAccess {

    @Override
    @Invoker("removeWidget")
    public abstract void mainmenuRepair$removeWidget(GuiEventListener widget);
}
