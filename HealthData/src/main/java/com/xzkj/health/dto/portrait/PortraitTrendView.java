package com.xzkj.health.dto.portrait;

import java.util.List;

public record PortraitTrendView(
        List<String> dates,
        List<Integer> heartRates,
        List<Double> bloodOxygens
) {
}
