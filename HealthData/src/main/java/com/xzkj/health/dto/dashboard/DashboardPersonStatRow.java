package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardPersonStatRow {
    private String deptName;
    private String day;
    private Number personCount;
    private Number abnormalPersonCount;
}
