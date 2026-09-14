package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardMineEntryRow {
    private String empName;
    private String empCode;
    private String deptName;
    private String jobTypeName;
    private Number heartRate;
    private Number bloodOxygen;
    private Number systolic;
    private Number diastolic;
    private Number temperature;
    private String recordTime;
    private Number qualified;
}
