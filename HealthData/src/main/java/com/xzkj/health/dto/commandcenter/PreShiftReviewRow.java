package com.xzkj.health.dto.commandcenter;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PreShiftReviewRow {
    private LocalDate reviewDate;
    private String empCode;
    private LocalDateTime sourceRecordTime;
    private LocalDateTime reviewDeadline;
    private String reviewStatus;
    private String reviewResult;
    private String reviewOwner;
    private LocalDateTime reviewedAt;
    private Boolean overdue;
    private String remark;
}
