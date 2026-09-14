package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureTrendRow {
    private String date;
    private Number avgSystolic;
    private Number avgDiastolic;
}
