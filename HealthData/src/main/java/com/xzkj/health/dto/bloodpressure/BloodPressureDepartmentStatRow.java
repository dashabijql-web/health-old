package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureDepartmentStatRow {
    private String deptName;
    private Number avgSystolic;
    private Number avgDiastolic;
    private Number abnormalCount;
    private Number totalCount;
}
