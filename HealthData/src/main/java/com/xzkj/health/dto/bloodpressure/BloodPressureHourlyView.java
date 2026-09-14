package com.xzkj.health.dto.bloodpressure;

public record BloodPressureHourlyView(
        int hour,
        int avgSystolic,
        int avgDiastolic
) {
}
