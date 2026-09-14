package com.xzkj.health.dto.sleep;

public record SleepOverviewView(
        int uploadRate,
        int greenLineRate,
        String avgSleepTime,
        int avgScore,
        int totalCount
) {
}
