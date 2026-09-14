package com.xzkj.health.dto.heartrate;

public record HeartRateAbnormalRecordView(
        String recordTime,
        int heartRate,
        String direction,
        String level
) {
}
