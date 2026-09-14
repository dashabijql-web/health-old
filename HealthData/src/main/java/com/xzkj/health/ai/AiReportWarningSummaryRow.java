package com.xzkj.health.ai;

import lombok.Data;

@Data
public class AiReportWarningSummaryRow {
    private String warningType;
    private String warningLevel;
    private Number cnt;
}
