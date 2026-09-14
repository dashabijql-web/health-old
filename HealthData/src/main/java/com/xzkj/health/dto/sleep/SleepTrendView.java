package com.xzkj.health.dto.sleep;

import java.util.List;

public record SleepTrendView(
        List<String> dates,
        List<Double> avgData,
        List<Double> deepSleep,
        List<Double> lightSleep
) {
}
