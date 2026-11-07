package com.example.yudreampresence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试用日志收集器。 */
public final class TestLog implements PresenceLog {

    public final List<String> infos = new CopyOnWriteArrayList<>();
    public final List<String> warnings = new CopyOnWriteArrayList<>();
    public final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        infos.add(message);
    }

    @Override
    public void warn(String message) {
        warnings.add(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        warnings.add(message);
        errors.add(error);
    }
}
