package com.xzkj.health.dto.realtime;

import lombok.Data;

@Data
public class RealtimeOverviewRow {
    private Double avgHeartRate;
    private Double avgBloodOxygen;
    private Double avgSteps;
    private Double avgTemperature;
    private Double avgSleep;
    private Long todayWarningCount;
}
