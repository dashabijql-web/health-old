package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDailyHealthTrendRow {
    private String date;
    private Number avgHeartRate;
    private Number avgBloodOxygen;
    private Number avgSteps;
}
