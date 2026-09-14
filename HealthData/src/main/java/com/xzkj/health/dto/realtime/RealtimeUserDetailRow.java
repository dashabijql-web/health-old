package com.xzkj.health.dto.realtime;

import lombok.Data;

@Data
public class RealtimeUserDetailRow {
    private String userCode;
    private String userName;
    private String deptName;
    private Integer heartRate;
    private Integer bloodOxygen;
    private Double temperature;
    private Integer bloodPressureHigh;
    private Integer bloodPressureLow;
    private Integer pressure;
    private Integer steps;
    private Integer calories;
    private Double sleepHours;
    private String lastUpdate;
    private String status;
}
