package com.xzkj.health.dto.trendwarning;

public class TrendWarningDailyAverageRow {
    private String empCode;
    private String empName;
    private String deptName;
    private String recordDate;
    private Double avgHeartRate;
    private Double avgBloodOxygen;
    private Double avgTemperature;
    private Double avgBpHigh;
    private Double avgPressure;

    public TrendWarningDailyAverageRow() {
    }

    public TrendWarningDailyAverageRow(String empCode,
                                       String empName,
                                       String deptName,
                                       String recordDate,
                                       Double avgHeartRate,
                                       Double avgBloodOxygen,
                                       Double avgTemperature,
                                       Double avgBpHigh,
                                       Double avgPressure) {
        this.empCode = empCode;
        this.empName = empName;
        this.deptName = deptName;
        this.recordDate = recordDate;
        this.avgHeartRate = avgHeartRate;
        this.avgBloodOxygen = avgBloodOxygen;
        this.avgTemperature = avgTemperature;
        this.avgBpHigh = avgBpHigh;
        this.avgPressure = avgPressure;
    }

    public String getEmpCode() {
        return empCode;
    }

    public void setEmpCode(String empCode) {
        this.empCode = empCode;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public Double getAvgHeartRate() {
        return avgHeartRate;
    }

    public void setAvgHeartRate(Double avgHeartRate) {
        this.avgHeartRate = avgHeartRate;
    }

    public Double getAvgBloodOxygen() {
        return avgBloodOxygen;
    }

    public void setAvgBloodOxygen(Double avgBloodOxygen) {
        this.avgBloodOxygen = avgBloodOxygen;
    }

    public Double getAvgTemperature() {
        return avgTemperature;
    }

    public void setAvgTemperature(Double avgTemperature) {
        this.avgTemperature = avgTemperature;
    }

    public Double getAvgBpHigh() {
        return avgBpHigh;
    }

    public void setAvgBpHigh(Double avgBpHigh) {
        this.avgBpHigh = avgBpHigh;
    }

    public Double getAvgPressure() {
        return avgPressure;
    }

    public void setAvgPressure(Double avgPressure) {
        this.avgPressure = avgPressure;
    }
}
