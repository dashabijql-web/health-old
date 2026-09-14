package com.xzkj.health.dto.heartrate;

import java.util.List;

public record HeartRateAbnormalRecordPageView(
        List<HeartRateAbnormalRecordView> list,
        int total,
        int page,
        int size
) {
}
