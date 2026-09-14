package com.xzkj.health.config.datasource;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class HealthCacheKeys {

    private HealthCacheKeys() {
    }

    public static String currentSource() {
        HealthDataSourceKey key = HealthDataSourceContext.get();
        return key == null ? "default" : key.key();
    }

    public static String key(Object... parts) {
        String suffix = Arrays.stream(parts)
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
        return currentSource() + "|" + suffix;
    }
}
