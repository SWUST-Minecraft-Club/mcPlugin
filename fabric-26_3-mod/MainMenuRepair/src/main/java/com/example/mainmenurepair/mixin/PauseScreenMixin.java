package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.EmptyIconRow;
import com.example.mainmenurepair.util.StatusLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
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
 * <p>实现要点：
 * <ul>
 *     <li>所有注入都写 {@code remap = false}：26.3 不混淆，这里写的就是运行时名字；</li>
 *     <li><b>主路径</b>：构造函数（{@code <init>}，已验证可靠）里用
 *         {@code Minecraft#execute} 排一个任务，下一 tick 菜单已构建完成时再做清理 ——
 *         不依赖 {@code createPauseMenu} 这个选择器能否匹配；</li>
 *     <li>辅助路径：{@code @Redirect} 让图标行布局不接收子控件、{@code @ModifyArg} 把警告置空、
 *         {@code @Inject} 在 {@code createPauseMenu} 返回时也清理一次（都是幂等的）；</li>
 *     <li>清理结果会追加到游戏目录下的 {@code mainmenu-repair-status.txt}，便于排查。</li>
 * </ul>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    /** 主路径：构造完成后排一次清理（此时还没构建菜单，所以要等下一 tick） */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1, remap = false)
    private void mainmenuRepair$scheduleCleanup(final CallbackInfo ci) {
        final PauseScreen self = (PauseScreen) (Object) this;
        StatusLog.append("PauseScreen 已创建，排队清理");
        Minecraft.getInstance().execute(() -> mainmenuRepair$cleanup(self, 0));
    }

    /** 辅助路径：菜单构建返回时也清理一次（若该注入点匹配得上） */
    @Inject(method = "createPauseMenu", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$afterCreateMenu(final CallbackInfo ci) {
        mainmenuRepair$cleanup((PauseScreen) (Object) this, 0);
    }

    /** 精确拦截：让图标行布局不接收任何子控件（4 个按钮就不会被创建） */
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

    /** 幂等清理：隐藏并移除 4 个图标按钮，清空按钮上的提示 */
    @Unique
    private static void mainmenuRepair$cleanup(final PauseScreen screen, final int attempt) {
        final List<? extends GuiEventListener> children = new ArrayList<>(screen.children());

        int icons = 0;
        int removed = 0;
        for (final GuiEventListener child : children) {
            // SpriteIconButton 覆盖报告 Bug / 给予反馈 / 举报玩家，FriendsButton 也继承它
            if (!(child instanceof SpriteIconButton)) {
                continue;
            }
            icons++;
            if (child instanceof AbstractWidget widget) {
                // 先隐藏：即使下面的移除失败，玩家也看不到它们
                widget.visible = false;
                widget.active = false;
            }
            try {
                ((ScreenAccess) (Object) screen).mainmenuRepair$removeWidget(child);
                removed++;
            } catch (final Throwable ignored) {
                // 移除失败也没关系，已经隐藏了
            }
        }

        int tooltips = 0;
        for (final GuiEventListener child : screen.children()) {
            // 剩下的按钮里只有服务器自定义选项按钮带提示（那句第三方警告）
            if (child instanceof AbstractWidget widget) {
                widget.setTooltip(null);
                tooltips++;
            }
        }

        StatusLog.append("清理（第 " + (attempt + 1) + " 次）：控件 " + children.size() + " 个，"
                + "图标 " + icons + " 个（移除 " + removed + "），清空提示 " + tooltips + " 个");

        // 菜单还没构建完时（控件里找不到图标），下一 tick 再试，最多 4 次
        if (icons == 0 && attempt < 3) {
            Minecraft.getInstance().execute(() -> mainmenuRepair$cleanup(screen, attempt + 1));
        }
    }
}
