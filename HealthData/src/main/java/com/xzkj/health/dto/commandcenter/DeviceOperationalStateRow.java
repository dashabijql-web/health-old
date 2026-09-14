package com.xzkj.health.dto.commandcenter;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceOperationalStateRow {
    private Long deviceId;
    private String faultStatus;
    private String faultCode;
    private String faultDescription;
    private String handlingStatus;
    private String ownerName;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
    private String lastOperator;
    private String remark;
}
