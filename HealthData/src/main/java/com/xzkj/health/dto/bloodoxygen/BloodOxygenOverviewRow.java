package com.xzkj.health.dto.bloodoxygen;

import lombok.Data;

@Data
public class BloodOxygenOverviewRow {
    private Number avgBloodOxygen;
    private Number maxBloodOxygen;
    private Number minBloodOxygen;
    private Number normalCount;
    private Number abnormalCount;
    private Number totalCount;
    private Number detectionRate;
}
