package com.xzkj.health.dto.metric;

import java.util.List;

public record MetricRiskUserPageView(
        List<MetricRiskUserView> list,
        int total,
        int page,
        int size
) {
}
