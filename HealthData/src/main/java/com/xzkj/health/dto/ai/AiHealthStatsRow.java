package com.xzkj.health.dto.ai;

import lombok.Data;

@Data
public class AiHealthStatsRow {
    private Number recordCount;
    private Number empCount;
    private Number avgHeartRate;
    private Number maxHeartRate;
    private Number minHeartRate;
    private Number avgBloodOxygen;
    private Number minBloodOxygen;
    private Number avgTemperature;
    private Number avgSleepHours;
}
