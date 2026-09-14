package com.xzkj.health.dto.realtime;

import java.util.List;
import java.util.Map;

public record RealtimeUserView(
        Long id,
        String userCode,
        String userName,
        Integer gender,
        Integer age,
        String deptName,
        Integer heartRate,
        Integer bloodOxygen,
        Integer steps,
        Integer calories,
        Double temperature,
        Double sleepHours,
        Integer bloodPressureHigh,
        Integer bloodPressureLow,
        Integer pressure,
        String status,
        String severity,
        List<String> warningReasons,
        Map<String, String> indicatorStates,
        Map<String, String> indicatorTimes,
        String lastUpdate,
        Long dataAgeSeconds,
        String imei
) {
}
