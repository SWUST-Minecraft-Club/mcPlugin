package com.example.mainmenurepair.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * 把运行状态写到游戏目录下的 {@code mainmenu-repair-status.txt}。
 *
 * <p>为什么不只打日志：Minecraft 的 {@code latest.log} 由 log4j 写入，
 * {@code System.out.println} 不会出现在里面 —— 之前几轮排查就是被这点误导的。
 */
public final class StatusLog {

    public static final String FILE_NAME = "mainmenu-repair-status.txt";

    private StatusLog() {
    }

    /** 重新开始一份报告（客户端启动时调用） */
    public static void reset(final String header) {
        try {
            Files.writeString(new File(FILE_NAME).toPath(),
                    header + "\n时间：" + LocalDateTime.now() + "\n工作目录：" + System.getProperty("user.dir") + "\n",
                    StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // 写不了就算了
        }
    }

    /** 追加一行 */
    public static void append(final String line) {
        final String text = "[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line + "\n";
        try {
            Files.writeString(new File(FILE_NAME).toPath(), text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException ignored) {
            // 写不了就算了
        }
        System.out.print("[MainMenuRepair] " + line + "\n");
    }
}
