package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardWarningRateRow {
    private String name;
    private Number rate;
    private String icon;
}
