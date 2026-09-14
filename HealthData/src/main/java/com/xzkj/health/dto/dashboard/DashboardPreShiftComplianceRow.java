package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardPreShiftComplianceRow {
    private Number totalToday;
    private Number qualifiedCount;
    private Number failedCount;
}
