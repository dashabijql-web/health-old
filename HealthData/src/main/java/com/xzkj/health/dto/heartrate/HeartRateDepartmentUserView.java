package com.xzkj.health.dto.heartrate;

public record HeartRateDepartmentUserView(
        String userCode,
        String userName,
        String deptName,
        int abnormalCount,
        int anomalyDays,
        int lowCount,
        int highCount,
        int minHeartRate,
        int maxHeartRate,
        String lastRecordTime
) {
}
