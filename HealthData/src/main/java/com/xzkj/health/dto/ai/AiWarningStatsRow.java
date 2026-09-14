package com.xzkj.health.dto.ai;

import lombok.Data;

@Data
public class AiWarningStatsRow {
    private Number totalWarnings;
    private Number highRiskCount;
    private Number midRiskCount;
    private Number affectedEmp;
}
