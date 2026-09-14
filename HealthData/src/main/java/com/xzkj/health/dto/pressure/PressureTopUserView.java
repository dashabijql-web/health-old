package com.xzkj.health.dto.pressure;

public record PressureTopUserView(
        String userCode,
        String userName,
        String deptName,
        int avgPressure,
        int maxPressure,
        int count
) {
}
