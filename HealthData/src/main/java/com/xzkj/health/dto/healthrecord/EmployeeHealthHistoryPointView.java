package com.xzkj.health.dto.healthrecord;

public record EmployeeHealthHistoryPointView(
        String time,
        Double heartRate,
        Double bloodOxygen,
        Double temperature,
        Double systolic,
        Double diastolic,
        Double pressure,
        Integer steps,
        Integer calories,
        long sampleCount
) {
}
