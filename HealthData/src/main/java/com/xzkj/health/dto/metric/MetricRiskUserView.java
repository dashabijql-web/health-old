package com.xzkj.health.dto.metric;

public record MetricRiskUserView(
        String userCode,
        String userName,
        String deptName,
        int sampleCount,
        int abnormalCount,
        int anomalyDays,
        Integer primaryMin,
        Integer primaryMax,
        Integer secondaryMin,
        Integer secondaryMax,
        int riskCode,
        String lastSampleTime,
        String lastRecordTime
) {
}
