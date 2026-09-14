package com.xzkj.health.dto.pressure;

public record PressureDepartmentStatView(
        String deptName,
        int avgPressure,
        int highCount,
        int abnormalCount,
        int totalCount
) {
}
