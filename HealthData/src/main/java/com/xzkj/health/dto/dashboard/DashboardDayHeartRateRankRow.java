package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDayHeartRateRankRow {
    private String empName;
    private String deptName;
    private Number avgHeartRate;
}
