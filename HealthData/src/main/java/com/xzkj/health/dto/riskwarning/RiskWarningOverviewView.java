package com.xzkj.health.dto.riskwarning;

public record RiskWarningOverviewView(
        int heartRateCount,
        int sleepCount,
        int bloodOxygenCount,
        int temperatureCount,
        int pressureCount,
        int totalWarnings,
        int handledWarnings,
        int pendingWarnings,
        int dangerCount,
        int warningCount,
        int handledRate
) {
}
