package com.xzkj.health.dto.commandcenter;

public record DeviceFaultRequest(
        String faultCode,
        String faultDescription,
        String remark
) {
}
