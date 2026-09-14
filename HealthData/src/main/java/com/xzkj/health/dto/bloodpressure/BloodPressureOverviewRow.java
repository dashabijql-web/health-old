package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureOverviewRow {
    private Number avgSystolic;
    private Number avgDiastolic;
    private Number minSystolic;
    private Number maxSystolic;
    private Number minDiastolic;
    private Number maxDiastolic;
    private Number detectionCount;
    private Number totalCount;
    private Number normalRate;
    private Number abnormalCount;
    private Number elevatedRate;
    private Number hypertensionRate;
}
