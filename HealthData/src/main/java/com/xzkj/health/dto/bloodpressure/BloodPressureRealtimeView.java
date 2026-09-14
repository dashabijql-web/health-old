package com.xzkj.health.dto.bloodpressure;

public record BloodPressureRealtimeView(
        String userCode,
        String userName,
        String deptName,
        Integer systolic,
        Integer diastolic,
        String recordTime
) {
}
