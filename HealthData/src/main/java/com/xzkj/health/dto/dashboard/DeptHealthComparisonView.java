package com.xzkj.health.dto.dashboard;

public record DeptHealthComparisonView(
        String deptName,
        int memberCount,
        Double avgHeartRate,
        Double avgBloodOxygen,
        Double avgSystolic,
        Double avgSleepMinutes,
        Double avgSteps,
        Double avgPressure
) {
}
