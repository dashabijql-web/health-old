package com.xzkj.health.dto.dashboard;

public record MineEntryView(
        String empName,
        String empCode,
        String deptName,
        String jobTypeName,
        Integer heartRate,
        Integer bloodOxygen,
        Integer systolic,
        Integer diastolic,
        Integer temperature,
        String recordTime,
        boolean qualified
) {
}
