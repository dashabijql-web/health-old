package com.xzkj.health.dto.statistics;

public record MonthlySummaryView(
        String empCode,
        String empName,
        Long deptId,
        String deptName,
        int recordCount,
        Double avgHeartRate,
        Double avgBloodOxygen,
        Double avgTemperature,
        int healthScore
) {
}
