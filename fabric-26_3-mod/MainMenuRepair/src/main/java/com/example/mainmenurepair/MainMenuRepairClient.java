package com.example.mainmenurepair;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.MenuCleaner;
import com.example.mainmenurepair.util.StatusLog;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端入口：注册清理钩子，并把状态写到游戏目录下的 {@code mainmenu-repair-status.txt}。
 *
 * <p>清理有两套互相独立的机制（任一生效即可）：
 * <ol>
 *     <li><b>Fabric API 屏幕事件</b>：{@code ScreenEvents.AFTER_INIT}（需要客户端装 Fabric API）；</li>
 *     <li><b>Mixin</b>：{@code Screen#init(int,int)} / {@code Gui#setScreen(Screen)} 返回时调用。</li>
 * </ol>
 */
public final class MainMenuRepairClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StatusLog.reset("MainMenuRepair 状态报告");
        reportMixinStatus();
        registerScreenEvents();
    }

    private void reportMixinStatus() {
        try {
            final Class<?> pauseScreen = Class.forName("net.minecraft.client.gui.screens.PauseScreen");
            final Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen");
            final Class<?> gui = Class.forName("net.minecraft.client.gui.Gui");
            StatusLog.append("ScreenAccess 已混入 Screen：" + ScreenAccess.class.isAssignableFrom(screen));
            StatusLog.append("ScreenInitMixin 已合并到 Screen：" + hasMethod(screen, "mainmenuRepair$afterInit"));
            StatusLog.append("GuiSetScreenMixin 已合并到 Gui：" + hasMethod(gui, "mainmenuRepair$afterSetScreen"));
            StatusLog.append("PauseScreenMixin 已合并到 PauseScreen：" + hasMethod(pauseScreen, "mainmenuRepair$onConstructed"));
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
            StatusLog.append("Fabric API 钩子注册失败（客户端可能没装 Fabric API）：" + throwable);
        }
    }

    private static boolean hasMethod(final Class<?> type, final String name) {
        for (final Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
