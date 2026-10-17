package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.util.MenuCleaner;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第二个独立入口：{@code Gui#setScreen(Screen)} 是所有界面切换的必经之路
 * （{@code Minecraft#setScreenAndShow} 内部就是调它）。多一个目标多一层保障。
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {

    @Inject(method = "setScreen", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$afterSetScreen(final Screen screen, final CallbackInfo ci) {
        MenuCleaner.clean(screen, "Gui#setScreen");
    }
}
