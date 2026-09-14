package com.xzkj.health.dto.dashboard;

public record PersonCountsView(
        int heartRate,
        int bloodOxygen,
        int sleep,
        int steps,
        int temperature,
        int pressure,
        int totalPersons
) {
}
