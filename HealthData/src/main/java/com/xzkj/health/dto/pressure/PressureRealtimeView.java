package com.xzkj.health.dto.pressure;

public record PressureRealtimeView(
        String userCode,
        String userName,
        String deptName,
        Integer pressure,
        String recordTime
) {
}
