package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDeviceStatsRow {
    private Number total;
    private Number boundDevices;
    private Number activeRate;
    private Number usageRate;
    private Number warningRate;
    private Number lowBattery;
}
