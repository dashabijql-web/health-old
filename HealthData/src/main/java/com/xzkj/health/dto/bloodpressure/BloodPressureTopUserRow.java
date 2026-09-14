package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureTopUserRow {
    private String userCode;
    private String userName;
    private String deptName;
    private Number avgSystolic;
    private Number avgDiastolic;
    private Number count;
}
