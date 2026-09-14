package com.xzkj.health.ai;

import lombok.Data;

@Data
public class AiReportDepartmentInfoRow {
    private Long id;
    private String deptName;
    private String riskLevel;
    private Number empCount;
}
