package com.example.mainmenurepair;

import com.example.mainmenurepair.access.ScreenAccess;
import com.example.mainmenurepair.util.StatusLog;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端入口：写一份状态报告，方便确认模组与 mixin 是否真的生效。
 */
public final class MainMenuRepairClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StatusLog.reset("MainMenuRepair 状态报告");
        try {
            final Class<?> pauseScreen = Class.forName("net.minecraft.client.gui.screens.PauseScreen");
            final Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen");
            final Class<?> gui = Class.forName("net.minecraft.client.gui.Gui");
            StatusLog.append("PauseScreen 可加载：是");
            StatusLog.append("ScreenAccess 已混入 Screen：" + ScreenAccess.class.isAssignableFrom(screen));
            StatusLog.append("ScreenInitMixin 已合并到 Screen：" + hasMethod(screen, "mainmenuRepair$afterInit"));
            StatusLog.append("GuiSetScreenMixin 已合并到 Gui：" + hasMethod(gui, "mainmenuRepair$afterSetScreen"));
            StatusLog.append("PauseScreenMixin 已合并到 PauseScreen：" + hasMethod(pauseScreen, "mainmenuRepair$onConstructed"));
        } catch (final Throwable throwable) {
            StatusLog.append("检查失败：" + throwable);
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
