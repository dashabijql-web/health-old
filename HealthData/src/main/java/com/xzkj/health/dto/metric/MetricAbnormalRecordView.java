package com.xzkj.health.dto.metric;

public record MetricAbnormalRecordView(
        String recordTime,
        Integer primaryValue,
        Integer secondaryValue,
        String direction,
        String level
) {
}
