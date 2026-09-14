package com.xzkj.health.dto.employee;

public record EmployeeCommandSearchView(
        Long employeeId,
        String empCode,
        String empName,
        String deptName,
        String jobTypeName,
        String phone,
        String imei,
        boolean online,
        String deviceLastOnlineTime
) {
}
