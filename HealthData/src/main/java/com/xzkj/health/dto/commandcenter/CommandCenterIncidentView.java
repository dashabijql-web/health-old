package com.xzkj.health.dto.commandcenter;

import java.util.List;

/**
 * A read model over existing warning records. Location, owner and SLA retain an
 * explicit unavailable state until the corresponding operational systems are wired.
 */
public record CommandCenterIncidentView(
        String incidentId,
        Long warningId,
        String source,
        String type,
        String typeLabel,
        String severity,
        String status,
        String occurredAt,
        String updatedAt,
        long durationSeconds,
        Person person,
        Location location,
        VitalSnapshot vitalSnapshot,
        Owner owner,
        Sla sla,
        List<String> availableActions
) {
    public record Person(String userCode, String name, String department) {
    }

    public record Location(String status, String label, String updatedAt) {
    }

    public record VitalSnapshot(String indicator, String value) {
    }

    public record Owner(String status, Long userId, String name, String department) {
    }

    public record Sla(boolean configured, String status, String deadlineAt, String message) {
    }
}
