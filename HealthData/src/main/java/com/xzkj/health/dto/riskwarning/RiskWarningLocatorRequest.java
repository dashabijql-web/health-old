package com.xzkj.health.dto.riskwarning;

public record RiskWarningLocatorRequest(
        Long warningId,
        String occurredAt
) {
}
