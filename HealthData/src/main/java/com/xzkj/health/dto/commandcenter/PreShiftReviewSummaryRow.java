package com.xzkj.health.dto.commandcenter;

import lombok.Data;

@Data
public class PreShiftReviewSummaryRow {
    private Number awaitingReview;
    private Number retestOverdue;
}
