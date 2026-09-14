package com.xzkj.health.dto.metric;

public record MetricDailyRiskView(
        String date,
        int coveredUsers,
        int anomalyCount,
        double anomalyRate,
        int abnormalRecords,
        int totalRecords
) {
}
