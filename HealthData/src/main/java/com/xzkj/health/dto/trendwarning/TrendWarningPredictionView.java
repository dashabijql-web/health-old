package com.xzkj.health.dto.trendwarning;

import java.util.List;

public record TrendWarningPredictionView(
        List<TrendWarningEmployeeView> list,
        TrendWarningSummaryView summary
) {
}
