package com.xzkj.health.dto.healthrecord;

import lombok.Data;

@Data
public class EmployeeHealthHistoryRow {
    private String bucketTime;
    private Double avgHeartRate;
    private Double avgBloodOxygen;
    private Double avgTemperature;
    private Double avgSystolic;
    private Double avgDiastolic;
    private Double avgPressure;
    private Integer maxSteps;
    private Integer maxCalories;
    private Long sampleCount;
}
