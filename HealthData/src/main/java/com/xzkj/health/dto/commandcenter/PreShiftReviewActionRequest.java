package com.xzkj.health.dto.commandcenter;

public record PreShiftReviewActionRequest(
        String reviewDate,
        String sourceRecordTime,
        String action,
        String remark
) {
}
