package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDepartmentHealthCountRow {
    private String name;
    private Number count;
    private Number prevCount;
}
