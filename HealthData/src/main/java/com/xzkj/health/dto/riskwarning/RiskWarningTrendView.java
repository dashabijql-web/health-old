package com.xzkj.health.dto.riskwarning;

import java.util.List;

public record RiskWarningTrendView(
        List<String> dates,
        RiskWarningTrendSeriesView series
) {
}
