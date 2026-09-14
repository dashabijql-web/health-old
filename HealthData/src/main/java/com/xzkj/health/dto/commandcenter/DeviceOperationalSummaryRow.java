package com.xzkj.health.dto.commandcenter;

import lombok.Data;

@Data
public class DeviceOperationalSummaryRow {
    private Number total;
    private Number online;
    private Number lowBattery;
    private Number dataInterrupted;
    private Number faulted;
}
