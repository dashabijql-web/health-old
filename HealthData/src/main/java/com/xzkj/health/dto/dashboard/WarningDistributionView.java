package com.xzkj.health.dto.dashboard;

import java.util.List;

public record WarningDistributionView(
        List<String> labels,
        List<Integer> counts
) {
}
