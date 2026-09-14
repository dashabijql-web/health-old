package com.xzkj.health.dto.dashboard;

public record PreShiftComplianceView(
        int totalToday,
        int qualifiedCount,
        int failedCount,
        int preShiftRate
) {
}
