package com.xzkj.health.dto.commandcenter;

public record CommandCenterActionResultView(
        String actionId,
        String action,
        String status,
        String message,
        String target,
        String operator,
        String createdAt,
        CommandCenterIncidentView incident
) {
}
