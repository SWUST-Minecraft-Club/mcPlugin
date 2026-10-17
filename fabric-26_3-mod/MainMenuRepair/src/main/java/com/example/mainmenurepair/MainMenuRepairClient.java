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
            StatusLog.append("PauseScreen 可加载：是");
            StatusLog.append("ScreenInvokerMixin 生效（PauseScreen 实现 ScreenAccess）："
                    + ScreenAccess.class.isAssignableFrom(pauseScreen));
            StatusLog.append("PauseScreenMixin 实例注入方法已合并（mainmenuRepair$cleanupOnInit）："
                    + hasMethod(pauseScreen, "mainmenuRepair$cleanupOnInit"));
            StatusLog.append("PauseScreenMixin 构造函数注入方法已合并（mainmenuRepair$logApplied）："
                    + hasMethod(pauseScreen, "mainmenuRepair$logApplied"));
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
