package com.xzkj.health.dto.dashboard;

public record DayWarningView(
        String empName,
        String deptName,
        String warningType,
        String indicatorName,
        String warningValue,
        String warningLevel,
        String createTime
) {
}
