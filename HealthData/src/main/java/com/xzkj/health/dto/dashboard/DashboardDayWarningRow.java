package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDayWarningRow {
    private String empName;
    private String deptName;
    private String warningType;
    private String indicatorName;
    private String warningValue;
    private String warningLevel;
    private String createTime;
}
