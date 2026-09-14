package com.xzkj.health.dto.realtime;

public record RealtimeMonitorSummaryView(
        int onlineCount,
        int normalCount,
        int warningCount,
        int staleCount,
        int noDataCount,
        int onlineWindowMinutes,
        int freshnessMinutes
) {
}
