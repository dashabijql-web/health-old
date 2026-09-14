package com.xzkj.health.dto.commandcenter;

public record DeviceOperationalSummaryView(
        int total,
        int online,
        int offline,
        int onlineRate,
        int lowBattery,
        int dataInterrupted,
        int faulted
) {
}
