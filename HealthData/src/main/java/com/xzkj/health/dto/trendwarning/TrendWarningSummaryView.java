package com.xzkj.health.dto.trendwarning;

public record TrendWarningSummaryView(
        int total,
        int highRisk,
        int mediumRisk,
        int lowRisk,
        int normal
) {
}
