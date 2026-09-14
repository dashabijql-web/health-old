package com.xzkj.health.dto.heartrate;

import java.util.List;

public record HeartRateTrendView(
        List<String> dates,
        List<Integer> values
) {
}
