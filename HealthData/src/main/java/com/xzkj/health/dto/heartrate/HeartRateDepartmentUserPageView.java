package com.xzkj.health.dto.heartrate;

import java.util.List;

public record HeartRateDepartmentUserPageView(
        List<HeartRateDepartmentUserView> list,
        int total,
        int page,
        int size
) {
}
