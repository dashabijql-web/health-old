package com.xzkj.health.dto.bloodpressure;

import java.util.List;

public record BloodPressureTrendView(
        List<String> dates,
        List<Integer> systolicValues,
        List<Integer> diastolicValues
) {
}
