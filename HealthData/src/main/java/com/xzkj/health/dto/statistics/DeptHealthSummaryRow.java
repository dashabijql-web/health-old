package com.xzkj.health.dto.statistics;

import lombok.Data;

@Data
public class DeptHealthSummaryRow {
    private Number id;
    private String deptName;
    private Number employeeCount;
    private Number warningCount;
}
