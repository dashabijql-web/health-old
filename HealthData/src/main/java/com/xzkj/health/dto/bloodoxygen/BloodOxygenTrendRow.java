package com.xzkj.health.dto.bloodoxygen;

import lombok.Data;

@Data
public class BloodOxygenTrendRow {
    private String date;
    private Number avgBloodOxygen;
    private Number maxBloodOxygen;
    private Number minBloodOxygen;
}
