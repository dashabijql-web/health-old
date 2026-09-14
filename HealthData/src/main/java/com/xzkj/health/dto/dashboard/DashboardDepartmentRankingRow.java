package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDepartmentRankingRow {
    private String department;
    private Number memberCount;
    private Number healthScore;
}
