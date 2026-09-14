package com.xzkj.health.dto.heartrate;

public record HeartRateDepartmentStatView(
        String deptName,
        int avgHeartRate,
        int lowCount,
        int highCount,
        int abnormalCount,
        int totalCount
) {
}
