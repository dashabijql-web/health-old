package com.xzkj.health.dto.dashboard;

import java.util.List;

public record DeviceActivationView(
        DeviceStatsView stats,
        List<WarningRateView> warningRates
) {
}
