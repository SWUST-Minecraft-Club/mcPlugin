package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.util.EmptyIconRow;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 清理 ESC 暂停菜单（目标与签名取自 26.3 官方未混淆客户端 jar）。
 *
 * <p>两处改动：
 * <ol>
 *     <li><b>去掉“第三方内容”警告</b>：客户端在 {@code addCustomDialogButtons} 里对每个
 *         {@code pause_screen_additions} 按钮都无条件设置了 {@code CUSTOM_OPTIONS_TOOLTIP}
 *         （语言 key {@code menu.custom_options.tooltip}），这里把它的参数置空。</li>
 *     <li><b>移除右下角 4 个原版图标按钮</b>：它们在 {@code createPauseMenu()} 里被加进同一个
 *         {@code LinearLayout.horizontal()} 布局，这里把该布局换成不接收子控件的
 *         {@link EmptyIconRow}，四个按钮就不会被创建。</li>
 * </ol>
 *
 * <p>{@code require = 0}：将来版本改了方法名或调用点也不会让客户端崩溃，只是这一项改动不生效。
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    /** 只在第一次替换图标行时打一行日志 */
    @Unique
    private static boolean mainmenuRepair$logged;

    @ModifyArg(
            method = "addCustomDialogButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/Button$Builder;"
                            + "tooltip(Lnet/minecraft/client/gui/components/Tooltip;)"
                            + "Lnet/minecraft/client/gui/components/Button$Builder;"),
            require = 0)
    private Tooltip mainmenuRepair$removeThirdPartyWarning(final Tooltip tooltip) {
        return null;
    }

    @Redirect(
            method = "createPauseMenu",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;"
                            + "horizontal()Lnet/minecraft/client/gui/layouts/LinearLayout;"),
            require = 0)
    private LinearLayout mainmenuRepair$removeIconButtons() {
        if (!mainmenuRepair$logged) {
            mainmenuRepair$logged = true;
            System.out.println("[MainMenuRepair] 已接管 ESC 暂停菜单：右下角图标行不再创建，"
                    + "自定义选项按钮的第三方内容警告已去掉");
        }
        return new EmptyIconRow();
    }
}
