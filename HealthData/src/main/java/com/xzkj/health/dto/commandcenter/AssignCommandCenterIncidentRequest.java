package com.xzkj.health.dto.commandcenter;

public record AssignCommandCenterIncidentRequest(
        String occurredAt,
        Long ownerUserId,
        Integer slaMinutes,
        String remark
) {
}
