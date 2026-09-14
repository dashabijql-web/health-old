package com.xzkj.health.dto.heartrate;

public record HeartRateDailyAnomalyView(
        String date,
        int anomalyCount,
        int coveredUsers,
        double anomalyRate,
        int lowCount,
        int highCount,
        int abnormalRecords,
        int totalRecords
) {
}
