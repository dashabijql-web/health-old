package com.xzkj.health.dto.riskwarning;

public record RiskWarningDeptStatView(
        String deptName,
        int heartRate,
        int bloodOxygen,
        int sleep,
        int temperature,
        int pressure,
        int total
) {
}
