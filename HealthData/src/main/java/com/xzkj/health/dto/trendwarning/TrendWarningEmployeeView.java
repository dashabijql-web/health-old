package com.xzkj.health.dto.trendwarning;

import java.util.List;

public record TrendWarningEmployeeView(
        String empCode,
        String empName,
        String deptName,
        int riskLevel,
        List<TrendWarningMetricView> riskMetrics,
        List<String> dates
) {
}
