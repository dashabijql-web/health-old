package com.xzkj.health.dto.commandcenter;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PreShiftReviewCandidateRow {
    private String empCode;
    private LocalDateTime sourceRecordTime;
    private Number qualified;
}
