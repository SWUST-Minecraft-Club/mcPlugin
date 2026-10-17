package com.example.mainmenurepair;

import com.example.mainmenurepair.access.ScreenAccess;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端入口：只做一件事 —— 把模组的生效状态写到 {@code mainmenu-repair-status.txt}（游戏目录下）。
 *
 * <p>为什么不只打日志：Minecraft 的 {@code latest.log} 由 log4j 写入，
 * {@code System.out.println} 并不会出现在里面，之前几轮排查就是被这点误导的。
 */
public final class MainMenuRepairClient implements ClientModInitializer {

    private static final String STATUS_FILE = "mainmenu-repair-status.txt";

    @Override
    public void onInitializeClient() {
        final StringBuilder text = new StringBuilder();
        text.append("MainMenuRepair 状态报告\n");
        text.append("时间：").append(LocalDateTime.now()).append('\n');
        text.append("工作目录：").append(System.getProperty("user.dir")).append('\n');

        try {
            final Class<?> pauseScreen = Class.forName("net.minecraft.client.gui.screens.PauseScreen");
            text.append("PauseScreen 可加载：是\n");
            text.append("ScreenInvokerMixin 生效（PauseScreen 实现 ScreenAccess）：")
                    .append(ScreenAccess.class.isAssignableFrom(pauseScreen)).append('\n');
            text.append("PauseScreenMixin 生效（有 mainmenuRepair$cleanup 方法）：")
                    .append(hasMethod(pauseScreen, "mainmenuRepair$cleanup")).append('\n');
            text.append("构造函数诊断注入生效（有 mainmenuRepair$logApplied 方法）：")
                    .append(hasMethod(pauseScreen, "mainmenuRepair$logApplied")).append('\n');
        } catch (final Throwable throwable) {
            text.append("检查失败：").append(throwable).append('\n');
        }

        write(text.toString());
    }

    private static boolean hasMethod(final Class<?> type, final String name) {
        for (final Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void write(final String text) {
        try {
            Files.writeString(new File(STATUS_FILE).toPath(), text, StandardCharsets.UTF_8);
        } catch (final Exception ignored) {
            // 写不了就算了
        }
        System.out.print("[MainMenuRepair]\n" + text);
    }
}
