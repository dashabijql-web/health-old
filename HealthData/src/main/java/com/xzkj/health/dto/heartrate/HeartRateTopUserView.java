package com.xzkj.health.dto.heartrate;

public record HeartRateTopUserView(
        String userCode,
        String userName,
        int count,
        int anomalyDays
) {
}
