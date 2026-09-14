package com.xzkj.health.dto.dashboard;

public record DayBloodOxygenRankView(
        String empName,
        String deptName,
        Double avgBloodOxygen
) {
}
