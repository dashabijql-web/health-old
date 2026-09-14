package com.xzkj.health.dto.pressure;

public record PressureOverviewView(
        int avgPressure,
        int minPressure,
        int maxPressure,
        int detectionCount,
        int totalCount,
        int normalRate,
        int abnormalCount,
        int highCount
) {
}
