package com.xzkj.health.dto.bloodpressure;

public record BloodPressureOverviewView(
        int avgSystolic,
        int avgDiastolic,
        int minSystolic,
        int maxSystolic,
        int minDiastolic,
        int maxDiastolic,
        int detectionCount,
        int totalCount,
        int normalRate,
        int abnormalCount,
        int elevatedRate,
        int hypertensionRate
) {
}
