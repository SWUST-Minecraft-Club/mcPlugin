package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.EmptyIconRow;
import java.util.ArrayList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 清理 ESC 暂停菜单（目标与签名取自 26.3 官方未混淆客户端 jar）。
 *
 * <p>两件事：
 * <ol>
 *     <li><b>去掉“第三方内容”警告</b>：客户端在 {@code addCustomDialogButtons} 里对每个
 *         {@code pause_screen_additions} 按钮无条件设置 {@code CUSTOM_OPTIONS_TOOLTIP}。</li>
 *     <li><b>移除右下角 4 个原版图标按钮</b>（报告 Bug / 给予反馈 / 好友 / 举报玩家）。</li>
 * </ol>
 *
 * <p>做法分两层，互为兜底：
 * <ul>
 *     <li>第一层（精确）：{@code @Redirect} 让图标行布局不接收子控件 + {@code @ModifyArg} 把警告置空；</li>
 *     <li>第二层（兜底）：{@code @Inject} 在 {@code createPauseMenu()} 返回后遍历控件，
 *         先隐藏、再移除所有 {@link SpriteIconButton}（{@code FriendsButton} 也继承它），
 *         并清空按钮上的提示。即使第一层的注入点匹配失败（{@code require = 0} 会静默跳过），这一层也能生效。</li>
 * </ul>
 *
 * <p>除诊断注入外都用 {@code require = 0}：将来版本改了方法名/调用点也不会崩客户端。
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    /** 只打一次日志 */
    @Unique
    private static boolean mainmenuRepair$applied;

    /** 诊断：确认 mixin 真的被应用（第一次创建暂停菜单时打一行）。<init> 任何版本都有，用 require = 1 保证不静默。 */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void mainmenuRepair$logApplied(final CallbackInfo ci) {
        if (!mainmenuRepair$applied) {
            mainmenuRepair$applied = true;
            System.out.println("[MainMenuRepair] Mixin 已应用到 PauseScreen");
        }
    }

    /** 兜底清理：菜单构建完成后，直接遍历控件做清理 */
    @Inject(method = "createPauseMenu", at = @At("RETURN"), require = 0)
    private void mainmenuRepair$cleanup(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;

        int hidden = 0;
        int removed = 0;
        for (final GuiEventListener child : new ArrayList<>(self.children())) {
            // SpriteIconButton 覆盖报告 Bug / 给予反馈 / 举报玩家，FriendsButton 也继承它
            if (!(child instanceof SpriteIconButton)) {
                continue;
            }
            if (child instanceof AbstractWidget widget) {
                // 先隐藏：即使下面的移除失败，玩家也看不到它们
                widget.visible = false;
                widget.active = false;
                hidden++;
            }
            try {
                ((ScreenAccess) (Object) this).mainmenuRepair$removeWidget(child);
                removed++;
            } catch (final Throwable throwable) {
                // 移除失败也没关系，已经隐藏了
            }
        }

        int tooltips = 0;
        for (final GuiEventListener child : self.children()) {
            // 剩下的按钮里只有服务器自定义选项按钮带提示（那句第三方警告）
            if (child instanceof AbstractWidget widget) {
                widget.setTooltip(null);
                tooltips++;
            }
        }

        System.out.println("[MainMenuRepair] 暂停菜单清理：隐藏图标 " + hidden + " 个 / 移除 " + removed
                + " 个，清空 " + tooltips + " 个控件的提示");
    }

    /** 第一层：让图标行布局不接收任何子控件（4 个按钮就不会被创建） */
    @Redirect(
            method = "createPauseMenu",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;"
                            + "horizontal()Lnet/minecraft/client/gui/layouts/LinearLayout;"),
            require = 0)
    private LinearLayout mainmenuRepair$removeIconButtons() {
        return new EmptyIconRow();
    }

    /** 第一层：把自定义选项按钮的 tooltip 参数置空 */
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
}
