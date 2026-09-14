package com.xzkj.health.dto.dashboard;

public record DashboardBodyIndicatorsView(
        int avgPressure,
        int avgBloodOxygen,
        int avgHeartRate,
        int avgSteps,
        int avgBloodPressureHigh,
        int avgBloodPressureLow,
        int avgCalories,
        double avgSleep,
        double avgTemperature
) {
}
