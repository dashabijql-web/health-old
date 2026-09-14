package com.xzkj.health.dto.dashboard;

import java.util.List;

public record HealthTrendView(
        List<String> dates,
        List<Integer> heartRate,
        List<Integer> bloodOxygen,
        List<Integer> steps
) {
}
