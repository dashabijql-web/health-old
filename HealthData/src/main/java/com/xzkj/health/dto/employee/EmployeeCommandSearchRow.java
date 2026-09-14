package com.xzkj.health.dto.employee;

import lombok.Data;

@Data
public class EmployeeCommandSearchRow {
    private Long employeeId;
    private String empCode;
    private String empName;
    private String deptName;
    private String jobTypeName;
    private String phone;
    private String imei;
    private String deviceLastOnlineTime;
}
