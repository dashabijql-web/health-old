package com.xzkj.health.dto.dashboard;

public record DashboardOverviewView(
        int heartRate,
        int bloodOxygen,
        int sleep,
        int steps,
        int temperature,
        int pressure
) {
}
