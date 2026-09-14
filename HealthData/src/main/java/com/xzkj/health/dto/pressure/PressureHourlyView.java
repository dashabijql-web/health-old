package com.xzkj.health.dto.pressure;

public record PressureHourlyView(
        int hour,
        int avgPressure
) {
}
