package com.xzkj.health.dto.pressure;

import lombok.Data;

@Data
public class PressureOverviewRow {
    private Number avgPressure;
    private Number minPressure;
    private Number maxPressure;
    private Number detectionCount;
    private Number totalCount;
    private Number normalRate;
    private Number abnormalCount;
    private Number highCount;
}
