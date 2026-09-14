package com.xzkj.health.dto.bloodoxygen;

public record BloodOxygenRealtimeView(
        String userCode,
        String userName,
        String deptName,
        Integer bloodOxygen,
        String recordTime
) {
}
