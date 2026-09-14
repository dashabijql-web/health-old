package com.xzkj.health.dto.realtime;

public record RealtimeUserDetailView(
        String userCode,
        String userName,
        String deptName,
        Integer heartRate,
        Integer bloodOxygen,
        Double temperature,
        Integer bloodPressureHigh,
        Integer bloodPressureLow,
        Integer pressure,
        Integer steps,
        Integer calories,
        Double sleepHours,
        String lastUpdate,
        String status
) {
}
