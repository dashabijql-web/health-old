package com.xzkj.health.service;

import com.xzkj.health.config.CommandCenterOperationalProperties;
import com.xzkj.health.dto.commandcenter.PreShiftReviewActionRequest;
import com.xzkj.health.dto.commandcenter.PreShiftReviewCandidateRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryView;
import com.xzkj.health.dto.commandcenter.PreShiftReviewView;
import com.xzkj.health.mapper.PreShiftReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreShiftReviewServiceTest {

    private PreShiftReviewMapper mapper;
    private PreShiftReviewService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PreShiftReviewMapper.class);
        CommandCenterOperationalProperties properties = new CommandCenterOperationalProperties();
        properties.setPreShiftReviewMinutes(30);
        service = new PreShiftReviewService(mapper, properties);
        when(mapper.countSchemaTables()).thenReturn(1);
        when(mapper.getTodayCandidates()).thenReturn(List.of());
        when(mapper.getTodayReviews("ALL")).thenReturn(List.of());
    }

    @Test
    void summaryReadsCountsWithoutScanningHealthRecords() {
        PreShiftReviewSummaryRow row = new PreShiftReviewSummaryRow();
        row.setAwaitingReview(8);
        row.setRetestOverdue(3);
        when(mapper.getTodaySummary()).thenReturn(row);

        PreShiftReviewSummaryView result = service.getTodaySummary();

        assertEquals(8, result.awaitingReview());
        assertEquals(3, result.retestOverdue());
        verify(mapper, never()).getTodayCandidates();
    }

    @Test
    void insertsPendingReviewForNewAbnormalMeasurement() {
        PreShiftReviewCandidateRow candidate = new PreShiftReviewCandidateRow();
        candidate.setEmpCode("EMP009");
        candidate.setSourceRecordTime(LocalDateTime.of(2026, 7, 15, 8, 0));
        candidate.setQualified(0);
        when(mapper.getTodayCandidates()).thenReturn(List.of(candidate));
        when(mapper.getTodayReviews("ALL")).thenReturn(List.of());

        service.getTodayReviews("ALL");

        verify(mapper).insertPendingReview("EMP009", candidate.getSourceRecordTime(), 30);
    }

    @Test
    void claimUsesSourceRecordTimeAsOptimisticBoundary() {
        String today = LocalDate.now().toString();
        PreShiftReviewRow row = reviewRow(today, "EMP001", "PENDING");
        when(mapper.getReview(today, "EMP001")).thenReturn(row);
        when(mapper.updateReviewAction(
                eq(today), eq("EMP001"), anyString(), eq("IN_REVIEW"), eq(null),
                eq("admin"), eq("开始复检"), anyBoolean())).thenReturn(1);

        PreShiftReviewView result = service.applyAction(
                "EMP001",
                new PreShiftReviewActionRequest(today, "2026-07-15 08:00:00", "CLAIM", "开始复检"),
                "admin");

        assertEquals("PENDING", result.reviewStatus());
        verify(mapper).updateReviewAction(
                today, "EMP001", "2026-07-15 08:00:00", "IN_REVIEW", null,
                "admin", "开始复检", false);
    }

    private PreShiftReviewRow reviewRow(String date, String empCode, String status) {
        PreShiftReviewRow row = new PreShiftReviewRow();
        row.setReviewDate(LocalDate.parse(date));
        row.setEmpCode(empCode);
        row.setSourceRecordTime(LocalDateTime.of(2026, 7, 15, 8, 0));
        row.setReviewDeadline(LocalDateTime.of(2026, 7, 15, 8, 30));
        row.setReviewStatus(status);
        row.setOverdue(false);
        return row;
    }
}
