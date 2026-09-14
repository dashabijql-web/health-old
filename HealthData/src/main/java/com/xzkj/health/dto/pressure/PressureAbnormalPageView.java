package com.xzkj.health.dto.pressure;

import java.util.List;

public record PressureAbnormalPageView(
        List<PressureAbnormalRecordView> list,
        int total,
        int page,
        int size
) {
}
