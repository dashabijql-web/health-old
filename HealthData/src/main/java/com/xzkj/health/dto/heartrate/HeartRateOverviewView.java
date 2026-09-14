package com.xzkj.health.dto.heartrate;

public record HeartRateOverviewView(
        int avgHeartRate,
        int minHeartRate,
        int maxHeartRate,
        int detectionRate,
        int normalCount,
        int abnormalCount,
        int totalCount,
        int coveredUsers,
        int abnormalUsers,
        int lowUsers,
        int normalUsers,
        int elevatedUsers,
        int dangerUsers
) {
}
