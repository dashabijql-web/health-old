package com.xzkj.health.dto.commandcenter;

public record CommandCenterIncidentActionRequest(
        String occurredAt,
        String remark,
        String target
) {
}
