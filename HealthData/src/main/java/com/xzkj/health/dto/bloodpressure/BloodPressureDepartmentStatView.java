package com.xzkj.health.dto.bloodpressure;

public record BloodPressureDepartmentStatView(
        String deptName,
        int avgSystolic,
        int avgDiastolic,
        int abnormalCount,
        int totalCount
) {
}
