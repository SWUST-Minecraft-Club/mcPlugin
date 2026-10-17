package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.util.MenuCleaner;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主清理入口：{@code Screen#init(int, int)} 是 public final，每个界面初始化都会走它，
 * 返回时 {@code init()} 已执行完（暂停菜单的 createPauseMenu 也在其中），控件已挂到 {@code children()}。
 */
@Mixin(Screen.class)
public abstract class ScreenInitMixin {

    @Inject(method = "init(II)V", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$afterInit(final CallbackInfo ci) {
        MenuCleaner.clean((Screen) (Object) this, "Screen#init");
    }
}
