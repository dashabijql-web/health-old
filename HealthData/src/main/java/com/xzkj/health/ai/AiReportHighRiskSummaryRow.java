package com.xzkj.health.ai;

import lombok.Data;

@Data
public class AiReportHighRiskSummaryRow {
    private String empName;
    private String deptName;
    private String warningType;
    private String warningLevel;
    private Number cnt;
}
