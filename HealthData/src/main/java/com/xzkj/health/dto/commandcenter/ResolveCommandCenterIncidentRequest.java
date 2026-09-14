package com.xzkj.health.dto.commandcenter;

public record ResolveCommandCenterIncidentRequest(
        String occurredAt,
        String remark
) {
}
