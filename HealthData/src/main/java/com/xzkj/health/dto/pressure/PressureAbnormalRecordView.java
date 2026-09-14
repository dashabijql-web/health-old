package com.xzkj.health.dto.pressure;

public record PressureAbnormalRecordView(
        String userCode,
        String userName,
        String deptName,
        Integer pressure,
        String level,
        String recordTime
) {
}
