package com.xzkj.health.dto.bloodpressure;

import lombok.Data;

@Data
public class BloodPressureRealtimeRow {
    private String userCode;
    private String userName;
    private String deptName;
    private Number systolic;
    private Number diastolic;
    private String recordTime;
}
