package com.xzkj.health.dto.realtime;

import java.util.List;

public record RealtimeUserPageView(
        List<RealtimeUserView> list,
        int total,
        int page,
        int size,
        boolean stale,
        String refreshedAt,
        RealtimeMonitorSummaryView summary,
        List<String> departments,
        List<RealtimeUserView> warningPreview
) {
}
