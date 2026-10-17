package com.example.mainmenurepair.mixin;

import com.example.mainmenurepair.util.MenuCleaner;
import com.example.mainmenurepair.util.StatusLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 两个和界面切换相关的处理：
 *
 * <ol>
 *     <li><b>跳过中间 dialog</b>：ESC →「主菜单」在客户端会先打开 dialog 页面（服务端下发的 dialog），
 *         玩家还得再点一次。这里在切换界面时拦下它，直接发 {@code /cd} 让服务端打开 6×9 界面，
 *         做到 ESC →「主菜单」→ 一步到位。</li>
 *     <li><b>兜底清理</b>：界面初始化完成后调用 {@link MenuCleaner}（移除右下角图标、清空第三方警告）。</li>
 * </ol>
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {

    /** 拦下"主菜单" dialog 界面，改成直接请求服务端打开主菜单 */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void mainmenuRepair$skipOurDialog(final Screen screen, final CallbackInfo ci) {
        if (!MenuCleaner.isOurDialog(screen)) {
            return;
        }
        ci.cancel();
        final Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand("cd");
            StatusLog.append("拦下中间 dialog，直接发送 /cd");
        }
    }

    /** 界面切换完成后做一次清理（幂等） */
    @Inject(method = "setScreen", at = @At("RETURN"), require = 0, remap = false)
    private void mainmenuRepair$afterSetScreen(final Screen screen, final CallbackInfo ci) {
        MenuCleaner.clean(screen, "Gui#setScreen");
    }
}
