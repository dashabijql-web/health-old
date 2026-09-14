package com.xzkj.health.dto.bloodpressure;

import java.util.List;

public record BloodPressureAbnormalPageView(
        List<BloodPressureAbnormalRecordView> list,
        int total,
        int page,
        int size
) {
}
