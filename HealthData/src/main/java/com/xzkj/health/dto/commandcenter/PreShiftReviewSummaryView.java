package com.xzkj.health.dto.commandcenter;

public record PreShiftReviewSummaryView(
        int awaitingReview,
        int retestOverdue
) {
}
