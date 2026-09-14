package com.xzkj.health.dto.dashboard;

public record DeptDailyPersonView(
        String deptName,
        String day,
        int personCount
) {
}
