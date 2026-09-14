package com.xzkj.health.dto.riskwarning;

public record RiskWarningItemView(
        Long id,
        String userName,
        String userCode,
        String deptName,
        Integer gender,
        Integer age,
        String warningType,
        String warningLevel,
        String warningValue,
        String indicatorName,
        String eventSource,
        String eventCode,
        String deviceImei,
        String thresholdSnapshot,
        Boolean handled,
        String createTime,
        String handleBy,
        String handleTime,
        String handleNote
) {
}
