package com.xzkj.health.ai;

import lombok.Data;

@Data
public class AiReportHealthSummaryRow {
    private Number avgHeartRate;
    private Number minHeartRate;
    private Number maxHeartRate;
    private Number avgBloodOxygen;
    private Number minBloodOxygen;
    private Number avgBpHigh;
    private Number maxBpHigh;
    private Number avgBpLow;
    private Number maxBpLow;
    private Number avgTemperature;
    private Number maxTemperature;
    private Number avgSleepMinutes;
    private Number minSleepMinutes;
    private Number avgSteps;
    private Number avgCalories;
    private Number avgPressure;
    private Number maxPressure;
    private Number monitoredEmpCount;
    private Number recordCount;
}
