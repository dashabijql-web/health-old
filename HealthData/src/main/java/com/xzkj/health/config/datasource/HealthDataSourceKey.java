package com.xzkj.health.config.datasource;

import java.util.Locale;

public enum HealthDataSourceKey {
    OLD("old"),
    NEW("new");

    private final String key;

    HealthDataSourceKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static HealthDataSourceKey from(String raw, HealthDataSourceKey fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("old".equals(normalized) || "legacy".equals(normalized)) {
            return OLD;
        }
        if ("new".equals(normalized) || "fresh".equals(normalized)) {
            return NEW;
        }
        return fallback;
    }
}
