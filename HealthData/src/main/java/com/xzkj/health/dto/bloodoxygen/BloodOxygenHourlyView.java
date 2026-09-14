package com.xzkj.health.dto.bloodoxygen;

public record BloodOxygenHourlyView(
        int hour,
        double avgBloodOxygen
) {
}
