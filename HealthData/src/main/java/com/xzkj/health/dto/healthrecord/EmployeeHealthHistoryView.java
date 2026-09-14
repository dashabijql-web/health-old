package com.xzkj.health.dto.healthrecord;

import java.util.List;

public record EmployeeHealthHistoryView(
        String userCode,
        String startDate,
        String endDate,
        String granularity,
        long totalSamples,
        List<EmployeeHealthHistoryPointView> points
) {
}
