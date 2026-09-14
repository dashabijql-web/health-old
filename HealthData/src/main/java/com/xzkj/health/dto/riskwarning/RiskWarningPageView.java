package com.xzkj.health.dto.riskwarning;

import java.util.List;

public record RiskWarningPageView(
        List<RiskWarningItemView> list,
        int total,
        int page,
        int size
) {
}
