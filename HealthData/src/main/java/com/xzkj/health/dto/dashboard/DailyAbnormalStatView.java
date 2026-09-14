package com.xzkj.health.dto.dashboard;

public record DailyAbnormalStatView(
        String day,
        int personCount,
        int abnormalPersonCount
) {
}
