package com.xzkj.health.dto.metric;

public record MetricRiskSummaryView(
        int coveredUsers,
        int abnormalUsers,
        int abnormalRecords,
        int totalRecords,
        int normalUsers,
        int lowUsers,
        int warningUsers,
        int dangerUsers
) {
}
