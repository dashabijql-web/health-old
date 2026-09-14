package com.xzkj.health.dto.heartrate;

import java.util.List;

public record HeartRatePeriodUserPageView(
        List<HeartRatePeriodUserView> list,
        int total,
        int page,
        int size
) {
}
