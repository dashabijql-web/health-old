package com.xzkj.health.dto.portrait;

public record PortraitVitalsView(
        Integer heartRate,
        Integer bloodOxygen,
        Double temperature,
        Integer systolic,
        Integer diastolic,
        Integer pressure,
        Integer steps,
        Integer calories,
        String recordTime,
        Long dataAgeSeconds,
        String reportTime,
        Long reportAgeSeconds,
        String heartRateTime,
        String bloodOxygenTime,
        String temperatureTime,
        String bloodPressureTime,
        String pressureTime,
        boolean online,
        String freshnessStatus
) {
}
