package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDailyPersonRow {
    private String deptName;
    private String day;
    private Number personCount;
}
