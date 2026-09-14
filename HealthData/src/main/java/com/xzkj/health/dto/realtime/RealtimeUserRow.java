package com.xzkj.health.dto.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealtimeUserRow {
    private Long id;
    private String userCode;
    private String userName;
    private Integer gender;
    private Integer age;
    private String deptName;
    private Integer riskLevel;
    private Integer heartRate;
    private Integer bloodOxygen;
    private Integer steps;
    private Integer calories;
    private Double temperature;
    private Double sleepHours;
    private Integer bloodPressureHigh;
    private Integer bloodPressureLow;
    private Integer pressure;
    private String status;
    private String lastUpdate;
    private Long dataAgeSeconds;
    private Map<String, String> metricTimes;
    private String imei;
}
