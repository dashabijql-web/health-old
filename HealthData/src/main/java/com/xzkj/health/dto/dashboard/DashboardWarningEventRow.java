package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardWarningEventRow {
    private Object id;
    private String warningType;
    private String realName;
    private String empCode;
    private String indicatorName;
    private String indicatorValue;
    private String createTime;
    private String warningLevel;
    private Number handled;
}
