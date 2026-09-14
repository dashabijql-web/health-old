package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardHealthComparisonRow {
    private String deptName;
    private Number memberCount;
    private Number avgHeartRate;
    private Number avgBloodOxygen;
    private Number avgSystolic;
    private Number avgSleepMinutes;
    private Number avgSteps;
    private Number avgPressure;
}
