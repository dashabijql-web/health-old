package com.xzkj.health.dto.sleep;

public record SleepQualityDistributionView(
        int excellent,
        int good,
        int fair,
        int poor
) {
}
