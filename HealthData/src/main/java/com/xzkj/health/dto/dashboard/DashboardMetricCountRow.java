package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardMetricCountRow {
    private Number heartRate;
    private Number bloodOxygen;
    private Number sleep;
    private Number steps;
    private Number temperature;
    private Number pressure;
    private Number totalPersons;
}
