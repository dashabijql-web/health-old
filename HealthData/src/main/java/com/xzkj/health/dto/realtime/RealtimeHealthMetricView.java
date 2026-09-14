package com.xzkj.health.dto.realtime;

public record RealtimeHealthMetricView(
        String key,
        String label,
        String unit,
        Double average,
        Double minimum,
        Double maximum,
        Double p95,
        int coveredUsers,
        int abnormalUsers,
        double abnormalRate
) {
}
