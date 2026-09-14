package com.xzkj.health.dto.dashboard;

public record WarningEventView(
        Object id,
        String type,
        String userName,
        String empCode,
        String indicator,
        String value,
        String time,
        String level,
        int handled
) {
}
