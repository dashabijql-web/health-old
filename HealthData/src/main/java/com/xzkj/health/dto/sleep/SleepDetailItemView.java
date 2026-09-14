package com.xzkj.health.dto.sleep;

public record SleepDetailItemView(
        String userName,
        String deptName,
        String empCode,
        String sleepHours,
        Integer score,
        String level,
        String levelText,
        String recordTime
) {
}
