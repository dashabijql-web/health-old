package com.xzkj.health.dto.metric;

import java.util.List;

public record MetricAbnormalRecordPageView(
        List<MetricAbnormalRecordView> list,
        int total,
        int page,
        int size
) {
}
