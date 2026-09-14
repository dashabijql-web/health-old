package com.xzkj.health.dto.dashboard;

public record DeptPersonStatView(
        String deptName,
        int personCount,
        int abnormalPersonCount
) {
}
