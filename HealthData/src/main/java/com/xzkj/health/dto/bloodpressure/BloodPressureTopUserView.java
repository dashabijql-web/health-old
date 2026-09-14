package com.xzkj.health.dto.bloodpressure;

public record BloodPressureTopUserView(
        String userCode,
        String userName,
        String deptName,
        int avgSystolic,
        int avgDiastolic,
        int count
) {
}
