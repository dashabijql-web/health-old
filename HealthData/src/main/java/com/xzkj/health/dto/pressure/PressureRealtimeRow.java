package com.xzkj.health.dto.pressure;

import lombok.Data;

@Data
public class PressureRealtimeRow {
    private String userCode;
    private String userName;
    private String deptName;
    private Number pressure;
    private String recordTime;
}
