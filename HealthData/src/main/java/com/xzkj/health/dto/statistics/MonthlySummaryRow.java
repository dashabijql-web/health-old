package com.xzkj.health.dto.statistics;

import lombok.Data;

@Data
public class MonthlySummaryRow {
    private String empCode;
    private String empName;
    private Number deptId;
    private String deptName;
    private Number recordCount;
    private Number avgHeartRate;
    private Number avgBloodOxygen;
    private Number avgTemperature;
    private Number healthScore;
}
