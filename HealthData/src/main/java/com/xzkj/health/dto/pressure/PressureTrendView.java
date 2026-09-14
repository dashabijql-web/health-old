package com.xzkj.health.dto.pressure;

import java.util.List;

public record PressureTrendView(
        List<String> dates,
        List<Integer> values
) {
}
