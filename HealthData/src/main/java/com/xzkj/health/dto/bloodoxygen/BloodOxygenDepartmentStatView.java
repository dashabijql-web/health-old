package com.xzkj.health.dto.bloodoxygen;

public record BloodOxygenDepartmentStatView(
        String deptName,
        int avgBloodOxygen,
        int lowCount,
        int highCount,
        int totalCount
) {
}
