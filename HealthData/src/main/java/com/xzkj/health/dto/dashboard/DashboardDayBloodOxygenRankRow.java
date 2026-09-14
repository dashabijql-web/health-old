package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDayBloodOxygenRankRow {
    private String empName;
    private String deptName;
    private Number avgBloodOxygen;
}
