package com.xzkj.health.service.metric;

public enum MetricRiskType {
    PRESSURE("pressure"),
    BLOOD_PRESSURE("bloodPressure"),
    BLOOD_OXYGEN("bloodOxygen");

    private final String key;

    MetricRiskType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
