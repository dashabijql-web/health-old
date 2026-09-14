package com.xzkj.health.dto.realtime;

public record RealtimeOverviewView(
        long avgHeartRate,
        long avgBloodOxygen,
        long avgSteps,
        double avgTemperature,
        double avgSleep,
        long todayWarningCount
) {
}
