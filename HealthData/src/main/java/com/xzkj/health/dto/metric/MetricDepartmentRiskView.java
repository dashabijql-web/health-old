package com.xzkj.health.dto.metric;

public record MetricDepartmentRiskView(
        String deptName,
        int coveredUsers,
        int abnormalUsers,
        int abnormalRecords,
        int dangerRecords
) {
}
