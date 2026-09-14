package com.xzkj.health.dto.trendwarning;

import java.util.List;

public record TrendWarningMetricView(
        String metric,
        String metricName,
        String unit,
        double currentValue,
        double projectedValue,
        double threshold,
        double slope,
        int riskLevel,
        String riskDir,
        List<Double> history
) {
}
