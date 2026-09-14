package com.xzkj.health.dto.realtime;

public record RealtimeAlertView(
        Long id,
        String userCode,
        String userName,
        String deptName,
        String warningType,
        String indicatorName,
        String indicatorValue,
        String warningLevel,
        boolean handled,
        String createTime
) {
}
