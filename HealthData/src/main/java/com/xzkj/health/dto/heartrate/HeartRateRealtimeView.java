package com.xzkj.health.dto.heartrate;

public record HeartRateRealtimeView(
        String userCode,
        String empCode,
        String userName,
        String deptName,
        String gender,
        Integer age,
        String jobType,
        Integer heartRate,
        String recordTime
) {
}
