package com.xzkj.health.dto.statistics;

public record DeptHealthSummaryView(
        Long id,
        String deptName,
        int employeeCount,
        int warningCount
) {
}
