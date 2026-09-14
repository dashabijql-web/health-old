package com.xzkj.health.config.datasource;

import java.util.function.Supplier;

public final class HealthDataSourceContext {

    private static final ThreadLocal<HealthDataSourceKey> CURRENT = new ThreadLocal<>();

    private HealthDataSourceContext() {
    }

    public static HealthDataSourceKey get() {
        return CURRENT.get();
    }

    public static void set(HealthDataSourceKey key) {
        if (key == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(key);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runWith(HealthDataSourceKey key, Runnable action) {
        callWith(key, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callWith(HealthDataSourceKey key, Supplier<T> action) {
        HealthDataSourceKey previous = get();
        set(key);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
