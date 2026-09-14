package com.xzkj.health.dto.dashboard;

public record DeviceStatsView(
        int total,
        int boundDevices,
        int activeRate,
        int usageRate,
        int warningRate,
        int lowBattery
) {
}
