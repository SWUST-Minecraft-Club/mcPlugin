package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.util.EmptyIconRow;
import com.example.mainmenurepair.util.MenuCleaner;
import com.example.mainmenurepair.util.StatusLog;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 暂停菜单的精确拦截（可选层）：
 * <ul>
 *     <li>{@code @Redirect} 让右下角图标行布局不接收子控件，4 个按钮就不会被创建；</li>
 *     <li>{@code @ModifyArg} 把服务器自定义选项按钮上的“第三方内容”警告置空。</li>
 * </ul>
 *
 * <p>真正的兜底清理在 {@code ScreenInitMixin} / {@code GuiSetScreenMixin} 里（调用普通类
 * {@link MenuCleaner}），因为实测 {@code createPauseMenu} 这个方法选择器匹配不上。
 *
 * <p>注意：mixin 类里的 {@code private static} 辅助方法与 lambda 不会被合并进目标类，
 * 所以这里只调用普通类里的方法。
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    /** 诊断：<init> 任何版本都有，用 require = 1 保证不静默 */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1, remap = false)
    private void mainmenuRepair$onConstructed(final CallbackInfo ci) {
        StatusLog.append("PauseScreen 已构造");
        // 构造时菜单还没建，这里只是顺手试一次（清理是幂等的）
        MenuCleaner.clean((PauseScreen) (Object) this, "PauseScreen#<init>");
    }

    /** 精确拦截：图标行布局不接收任何子控件 */
    @Redirect(
            method = "createPauseMenu",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;"
                            + "horizontal()Lnet/minecraft/client/gui/layouts/LinearLayout;"),
            require = 0,
            remap = false)
    private LinearLayout mainmenuRepair$removeIconButtons() {
        return new EmptyIconRow();
    }

    /** 精确拦截：把自定义选项按钮的 tooltip 参数置空 */
    @ModifyArg(
            method = "addCustomDialogButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/Button$Builder;"
                            + "tooltip(Lnet/minecraft/client/gui/components/Tooltip;)"
                            + "Lnet/minecraft/client/gui/components/Button$Builder;"),
            require = 0,
            remap = false)
    private Tooltip mainmenuRepair$removeThirdPartyWarning(final Tooltip tooltip) {
        return null;
    }
}
