package com.xzkj.health.dto.heartrate;

public record HeartRatePeriodUserView(
        String userCode,
        String userName,
        String deptName,
        int sampleCount,
        int abnormalCount,
        int anomalyDays,
        int lowCount,
        int highCount,
        int minHeartRate,
        int maxHeartRate,
        int riskCode,
        String lastSampleTime,
        String lastRecordTime
) {
}
