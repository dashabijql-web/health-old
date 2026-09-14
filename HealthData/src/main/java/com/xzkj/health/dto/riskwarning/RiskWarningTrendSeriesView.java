package com.xzkj.health.dto.riskwarning;

import java.util.List;

public record RiskWarningTrendSeriesView(
        List<Integer> heartRate,
        List<Integer> bloodOxygen,
        List<Integer> sleep,
        List<Integer> temperature,
        List<Integer> pressure
) {
}
