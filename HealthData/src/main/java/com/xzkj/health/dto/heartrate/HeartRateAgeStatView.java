package com.xzkj.health.dto.heartrate;

public record HeartRateAgeStatView(
        String ageRange,
        int avgHeartRate
) {
}
