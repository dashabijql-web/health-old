package com.xzkj.health.dto.realtime;

public record RealtimeStatisticsView(
        long onlineUsers,
        long totalUsers,
        long weekRecords,
        long todayRecords,
        long onlineRate,
        long normalRate
) {
}
