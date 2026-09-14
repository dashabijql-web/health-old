package com.xzkj.health.dto.dashboard;

public record DailyAnomalyRateView(
        String date,
        double heartRateRate,
        double bloodOxygenRate,
        double temperatureRate,
        double pressureRate
) {
}
