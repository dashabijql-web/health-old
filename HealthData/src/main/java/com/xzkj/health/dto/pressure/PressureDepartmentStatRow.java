package com.xzkj.health.dto.pressure;

import lombok.Data;

@Data
public class PressureDepartmentStatRow {
    private String deptName;
    private Number avgPressure;
    private Number highCount;
    private Number abnormalCount;
    private Number totalCount;
}
