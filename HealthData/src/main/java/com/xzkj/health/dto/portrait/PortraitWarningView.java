package com.xzkj.health.dto.portrait;

public record PortraitWarningView(
        String warningType,
        String indicatorName,
        String warningValue,
        String warningLevel,
        String createTime
) {
}
