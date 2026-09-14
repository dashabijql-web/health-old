package com.xzkj.health.dto.realtime;

import lombok.Data;

@Data
public class RealtimeAlertRow {
    private Long id;
    private String userCode;
    private String userName;
    private String deptName;
    private String warningType;
    private String indicatorName;
    private String indicatorValue;
    private String warningLevel;
    private Integer handled;
    private String createTime;
}
