package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureHourlyRow {
    private Number hour;
    private Number avgSystolic;
    private Number avgDiastolic;
}
