package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardBodyAverageRow {
    private Number avgPressure;
    private Number avgBloodOxygen;
    private Number avgHeartRate;
    private Number avgSteps;
    private Number avgBloodPressureHigh;
    private Number avgBloodPressureLow;
    private Number avgCalories;
    private Number avgSleep;
    private Number avgTemperature;
}
