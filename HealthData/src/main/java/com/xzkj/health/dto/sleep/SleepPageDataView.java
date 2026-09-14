package com.xzkj.health.dto.sleep;

import java.util.List;

public record SleepPageDataView(
        SleepOverviewView overview,
        List<SleepLegendItemView> durationLegend,
        List<SleepLegendItemView> categoryLegend,
        List<SleepDeptUploadView> deptUpload,
        List<SleepDetailItemView> detailList,
        int durationTotal
) {
}
