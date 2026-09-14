package com.xzkj.health.dto.commandcenter;

import java.util.List;

public record CommandCenterIncidentPageView(
        List<CommandCenterIncidentView> items,
        int total,
        int page,
        int size,
        String scope,
        String windowStart,
        String windowEnd,
        String dataAsOf
) {
}
