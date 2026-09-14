package com.xzkj.health.dto.dashboard;

public record DayHeartRateRankView(
        String empName,
        String deptName,
        Integer avgHeartRate
) {
}
