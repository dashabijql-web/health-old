package com.xzkj.health.dto.realtime;

import java.util.List;

public record RealtimeHealthSnapshotView(
        String status,
        String refreshedAt,
        int onlineWindowMinutes,
        int freshnessMinutes,
        int onlineUsers,
        int freshUsers,
        int warningUsers,
        int staleUsers,
        int noDataUsers,
        double coverageRate,
        List<RealtimeHealthMetricView> metrics
) {
}
