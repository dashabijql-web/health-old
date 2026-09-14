package com.xzkj.health.dto.dashboard;

public record CalendarDayView(
        String date,
        Integer avgHeartRate,
        Integer avgBloodOxygen,
        Integer avgSteps,
        Integer warningCount
) {
}
