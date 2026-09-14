package com.xzkj.health.dto.bloodpressure;

public record BloodPressureAbnormalRecordView(
        String userCode,
        String userName,
        String deptName,
        Integer systolic,
        Integer diastolic,
        String level,
        String recordTime
) {
}
