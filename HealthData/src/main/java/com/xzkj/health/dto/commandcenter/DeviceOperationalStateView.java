package com.xzkj.health.dto.commandcenter;

public record DeviceOperationalStateView(
        Long deviceId,
        String faultStatus,
        String faultCode,
        String faultDescription,
        String handlingStatus,
        String ownerName,
        String detectedAt,
        String resolvedAt,
        String lastOperator,
        String remark
) {
}
