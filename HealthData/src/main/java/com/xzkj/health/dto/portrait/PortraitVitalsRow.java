package com.xzkj.health.dto.portrait;

import lombok.Data;

@Data
public class PortraitVitalsRow {
    private Integer heartRate;
    private Integer bloodOxygen;
    private Double temperature;
    private Integer systolic;
    private Integer diastolic;
    private Integer pressure;
    private Integer steps;
    private Integer calories;
    private String recordTime;
    private Long dataAgeSeconds;
    private String reportTime;
    private Long reportAgeSeconds;
    private String heartRateTime;
    private String bloodOxygenTime;
    private String temperatureTime;
    private String bloodPressureTime;
    private String pressureTime;
}
