package com.example.yudreampresence;

/**
 * 模组内部用的最小日志接口。
 *
 * <p>核心逻辑只依赖它，测试里可以换成收集器；真正的服务端实现用 SLF4J。
 */
public interface PresenceLog {

    void info(String message);

    void warn(String message);

    void warn(String message, Throwable error);
}
