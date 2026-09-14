package com.xzkj.health.dto.bloodoxygen;

import lombok.Data;

@Data
public class BloodOxygenAbnormalRecordRow {
    private Number id;
    private String userCode;
    private String userName;
    private String deptName;
    private Number bloodOxygen;
    private String recordTime;
    private String level;
}
