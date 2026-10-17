package com.example.mainmenurepair;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.MenuCleaner;
import com.example.mainmenurepair.util.StatusLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.phys.HitResult;

/**
 * 客户端入口，做两件事：
 *
 * <ol>
 *     <li><b>清理暂停菜单</b>：注册 {@code ScreenEvents.AFTER_INIT}，移除右下角 4 个原版图标按钮、
 *         清空服务器自定义选项按钮上的第三方警告（见 {@link MenuCleaner}）；</li>
 *     <li><b>补上"时钟右键空气"</b>：服务端不会为非可用物品的空气右键触发
 *         {@code PlayerInteractEvent}（javap 服务端 jar 确认过），所以这一条只能在客户端补：
 *         检测"按下使用键 + 瞄准空气 + 手持时钟"，直接给服务端发 {@code /cd}。</li>
 * </ol>
 *
 * <p>状态写到游戏目录下的 {@code mainmenu-repair-status.txt}。
 */
public final class MainMenuRepairClient implements ClientModInitializer {

    /** 上一 tick 是否按着使用键，用于识别"刚按下" */
    private boolean useKeyWasDown;

    @Override
    public void onInitializeClient() {
        StatusLog.reset("MainMenuRepair 状态报告");
        reportMixinStatus();
        registerScreenEvents();
        registerClockAirClick();
    }

    private void reportMixinStatus() {
        try {
            final Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen");
            StatusLog.append("ScreenAccess 已混入 Screen：" + ScreenAccess.class.isAssignableFrom(screen));
        } catch (final Throwable throwable) {
            StatusLog.append("mixin 状态检查失败：" + throwable);
        }
    }

    /** 注册 Fabric API 的界面初始化事件（客户端没装 Fabric API 时静默跳过） */
    private void registerScreenEvents() {
        try {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
                    (client, screen, scaledWidth, scaledHeight) ->
                            MenuCleaner.clean(screen, "ScreenEvents.AFTER_INIT"));
            StatusLog.append("Fabric API 钩子已注册：ScreenEvents.AFTER_INIT");
        } catch (final Throwable throwable) {
            StatusLog.append("ScreenEvents 注册失败（客户端可能没装 Fabric API）：" + throwable);
        }
    }

    /**
     * 时钟右键空气：客户端侧检测 + 发送 /cd。
     *
     * <p>只处理瞄准空气（{@code HitResult.Type.MISS}）的情况；对着方块的右键交给服务端插件处理，
     * 这样两边不会重复触发。
     */
    private void registerClockAirClick() {
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                final boolean down = client.options.keyUse.isDown();
                final boolean justPressed = down && !this.useKeyWasDown;
                this.useKeyWasDown = down;

                if (!justPressed || client.player == null) {
                    return;
                }
                // 有界面打开时鼠标不被捕获，此时不处理
                if (!client.mouseHandler.isMouseGrabbed()) {
                    return;
                }
                if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.MISS) {
                    return;
                }
                // 物品判断交给服务端（/cd clockair 会校验是否手持时钟），
                // 客户端不碰实体类签名，避免注解库缺失导致的编译问题
                client.player.connection.sendCommand("cd clockair");
                StatusLog.append("客户端检测到右键空气，已发送 /cd clockair");
            });
            StatusLog.append("Fabric API 钩子已注册：ClientTickEvents.END_CLIENT_TICK（右键空气）");
        } catch (final Throwable throwable) {
            StatusLog.append("ClientTickEvents 注册失败：" + throwable);
        }
    }
}
