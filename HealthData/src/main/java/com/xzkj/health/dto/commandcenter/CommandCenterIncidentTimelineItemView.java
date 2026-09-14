package com.xzkj.health.dto.commandcenter;

public record CommandCenterIncidentTimelineItemView(
        String actionId,
        String action,
        String result,
        String operator,
        String target,
        String remark,
        String createdAt
) {
}
