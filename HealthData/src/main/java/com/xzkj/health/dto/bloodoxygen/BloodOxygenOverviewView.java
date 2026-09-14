package com.xzkj.health.dto.bloodoxygen;

public record BloodOxygenOverviewView(
        int avgBloodOxygen,
        int maxBloodOxygen,
        int minBloodOxygen,
        int normalCount,
        int abnormalCount,
        int totalCount,
        int detectionRate
) {
}
