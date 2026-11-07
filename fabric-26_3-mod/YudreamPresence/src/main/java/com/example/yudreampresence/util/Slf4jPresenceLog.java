package com.example.yudreampresence.util;

import com.example.yudreampresence.PresenceLog;
import org.slf4j.Logger;

/** 把核心日志接口接到 Fabric / Minecraft 服务端使用的 SLF4J。 */
public final class Slf4jPresenceLog implements PresenceLog {

    private static final String PREFIX = "[YudreamPresence] ";

    private final Logger logger;

    public Slf4jPresenceLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(PREFIX + message);
    }

    @Override
    public void warn(String message) {
        logger.warn(PREFIX + message);
    }

    @Override
    public void warn(String message, Throwable error) {
        logger.warn(PREFIX + message, error);
    }
}
