package com.xzkj.health.dto.bloodoxygen;

import java.util.List;

public record BloodOxygenTrendView(
        List<String> dates,
        List<Integer> values
) {
}
