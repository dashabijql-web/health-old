package com.xzkj.health.dto.bloodoxygen;

import lombok.Data;

@Data
public class BloodOxygenDepartmentStatRow {
    private String deptName;
    private Number avgBloodOxygen;
    private Number lowCount;
    private Number highCount;
    private Number totalCount;
}
