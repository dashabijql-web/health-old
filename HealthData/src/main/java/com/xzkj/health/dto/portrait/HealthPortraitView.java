package com.xzkj.health.dto.portrait;

import java.util.List;

public record HealthPortraitView(
        String empName,
        String empCode,
        String deptName,
        String jobTypeName,
        Integer gender,
        String bloodType,
        Integer height,
        Integer weight,
        PortraitVitalsView vitals,
        PortraitExerciseView exercise,
        PortraitTrendView trend,
        List<PortraitWarningView> warnings,
        List<Integer> hourlyHr
) {
}
