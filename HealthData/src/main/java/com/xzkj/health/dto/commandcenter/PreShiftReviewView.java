package com.xzkj.health.dto.commandcenter;

public record PreShiftReviewView(
        String reviewDate,
        String empCode,
        String sourceRecordTime,
        String reviewDeadline,
        String reviewStatus,
        String reviewResult,
        String reviewOwner,
        String reviewedAt,
        boolean overdue,
        String remark
) {
}
