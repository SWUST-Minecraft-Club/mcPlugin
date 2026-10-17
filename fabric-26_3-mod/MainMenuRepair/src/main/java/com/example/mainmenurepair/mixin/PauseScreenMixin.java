package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.EmptyIconRow;
import com.example.mainmenurepair.util.StatusLog;
import java.util.ArrayList;
import java.util.List;
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
 * <p>踩过的坑（都写在这里避免以后再犯）：
 * <ul>
 *     <li>所有注入必须 {@code remap = false}：26.3 不混淆，目标名就是运行时名；</li>
 *     <li>清理代码必须写在<b>实例</b>注入方法里、全部内联：mixin 类里的 {@code private static}
 *         辅助方法和 lambda 都不会被合并进目标类，调用它们会在运行时抛 NoSuchMethodError；</li>
 *     <li>注入点选 {@code init()} 返回时（{@code Screen#init} 的覆写），此时菜单已构建完成、
 *         控件也已挂到 {@code Screen#children()}；{@code createPauseMenu} 这个名字的选择器
 *         实测匹配不上，所以只作为可选补充。</li>
 * </ul>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    /** 只打一次日志用 */
    @Unique
    private static boolean mainmenuRepair$logged;

    /** 诊断：确认 mixin 真的被应用（<init> 任何版本都有，用 require = 1 保证不静默） */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1, remap = false)
    private void mainmenuRepair$logApplied(final CallbackInfo ci) {
        if (!mainmenuRepair$logged) {
            mainmenuRepair$logged = true;
            StatusLog.append("构造函数注入已执行");
        }
    }

    /** 主清理：init() 返回时，菜单已构建完成 */
    @Inject(method = "init", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$cleanupOnInit(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        final List<GuiEventListener> children = new ArrayList<>(self.children());

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
                ((ScreenAccess) (Object) self).mainmenuRepair$removeWidget(child);
                removed++;
            } catch (final Throwable ignored) {
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

        StatusLog.append("init 清理：控件 " + children.size() + " 个，图标 " + icons
                + " 个（移除 " + removed + "），清空提示 " + tooltips + " 个");
    }

    /** 补充路径：菜单构建返回时也清理一次（该选择器实测可能匹配不上，能匹配上就多一层保障） */
    @Inject(method = "createPauseMenu", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$afterCreateMenu(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        int icons = 0;
        for (final GuiEventListener child : new ArrayList<>(self.children())) {
            if (child instanceof SpriteIconButton) {
                icons++;
            }
        }
        StatusLog.append("createPauseMenu 返回时：控件里有图标 " + icons + " 个");
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
}
