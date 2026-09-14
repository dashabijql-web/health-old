package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.CommandCenterOperationalProperties;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.HealthDataSourceKey;
import com.xzkj.health.dto.commandcenter.PreShiftReviewActionRequest;
import com.xzkj.health.dto.commandcenter.PreShiftReviewCandidateRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryView;
import com.xzkj.health.dto.commandcenter.PreShiftReviewView;
import com.xzkj.health.mapper.PreShiftReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreShiftReviewService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> LIST_STATUSES = Set.of("ALL", "PENDING", "IN_REVIEW", "COMPLETED", "OVERDUE");
    private static final Set<String> ACTIONS = Set.of("CLAIM", "REQUEST_RETEST", "CONFIRM_PROHIBITED");

    private final PreShiftReviewMapper reviewMapper;
    private final CommandCenterOperationalProperties properties;
    private final Set<String> initializedSources = ConcurrentHashMap.newKeySet();

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PreShiftReviewSummaryView getTodaySummary() {
        ensureSchema();
        PreShiftReviewSummaryRow row = reviewMapper.getTodaySummary();
        return new PreShiftReviewSummaryView(
                intValue(row == null ? null : row.getAwaitingReview()),
                intValue(row == null ? null : row.getRetestOverdue()));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PreShiftReviewView> getTodayReviews(String status) {
        syncTodayCandidates();
        String normalized = normalizeListStatus(status);
        return reviewMapper.getTodayReviews(normalized).stream().map(this::toView).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PreShiftReviewView applyAction(String empCode, PreShiftReviewActionRequest request, String operator) {
        if (empCode == null || empCode.isBlank()) {
            throw new BusinessException(400, "复检操作必须指定员工工号");
        }
        if (request == null || request.sourceRecordTime() == null || request.sourceRecordTime().isBlank()) {
            throw new BusinessException(400, "复检操作必须携带当前检测时间");
        }
        String reviewDate = normalizeDate(request.reviewDate());
        String action = request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw new BusinessException(400, "复检动作仅支持 CLAIM、REQUEST_RETEST 或 CONFIRM_PROHIBITED");
        }

        syncTodayCandidates();
        PreShiftReviewRow current = reviewMapper.getReview(reviewDate, empCode.trim());
        if (current == null) {
            throw new BusinessException(404, "未找到该员工当前复检任务");
        }
        if ("COMPLETED".equals(current.getReviewStatus())) {
            throw new BusinessException(409, "该轮复检已经完成");
        }

        boolean completed = "CONFIRM_PROHIBITED".equals(action);
        String status = completed ? "COMPLETED" : "IN_REVIEW";
        String result = switch (action) {
            case "REQUEST_RETEST" -> "RETEST_REQUIRED";
            case "CONFIRM_PROHIBITED" -> "PROHIBITED";
            default -> null;
        };
        int updated = reviewMapper.updateReviewAction(
                reviewDate,
                empCode.trim(),
                normalizeDateTime(request.sourceRecordTime()),
                status,
                result,
                operator,
                cleanRemark(request.remark()),
                completed);
        if (updated != 1) {
            throw new BusinessException(409, "检测数据或复检状态已变化，请刷新后重试");
        }
        return toView(reviewMapper.getReview(reviewDate, empCode.trim()));
    }

    private String sourceKey() {
        HealthDataSourceKey key = HealthDataSourceContext.get();
        return key == null ? "default" : key.key();
    }

    private void syncTodayCandidates() {
        ensureSchema();
        int reviewMinutes = Math.max(1, properties.getPreShiftReviewMinutes());
        List<PreShiftReviewCandidateRow> candidates = reviewMapper.getTodayCandidates();
        Map<String, PreShiftReviewRow> current = reviewMapper.getTodayReviews("ALL").stream()
                .collect(Collectors.toMap(PreShiftReviewRow::getEmpCode, Function.identity()));
        for (PreShiftReviewCandidateRow candidate : candidates) {
            if (candidate.getEmpCode() == null || candidate.getSourceRecordTime() == null) {
                continue;
            }
            boolean qualified = intValue(candidate.getQualified()) == 1;
            PreShiftReviewRow existing = current.get(candidate.getEmpCode());
            if (existing == null) {
                if (!qualified) {
                    try {
                        reviewMapper.insertPendingReview(
                                candidate.getEmpCode(), candidate.getSourceRecordTime(), reviewMinutes);
                    } catch (DataIntegrityViolationException ignored) {
                        // Concurrent dashboard refresh already inserted today's row.
                    }
                }
                continue;
            }
            if (existing.getSourceRecordTime() == null
                    || candidate.getSourceRecordTime().isAfter(existing.getSourceRecordTime())) {
                reviewMapper.updateFromLatestMeasurement(
                        candidate.getEmpCode(), candidate.getSourceRecordTime(), qualified, reviewMinutes);
            }
        }
    }

    private void ensureSchema() {
        String sourceKey = sourceKey();
        if (initializedSources.contains(sourceKey)) {
            return;
        }
        synchronized (initializedSources) {
            if (initializedSources.contains(sourceKey)) {
                return;
            }
            if (reviewMapper.countSchemaTables() != 1) {
                throw new BusinessException(503,
                        "班前复检表未迁移，请先执行 sql/command_center_operational_status.sql");
            }
            initializedSources.add(sourceKey);
        }
    }

    private PreShiftReviewView toView(PreShiftReviewRow row) {
        return new PreShiftReviewView(
                row.getReviewDate() == null ? null : DATE.format(row.getReviewDate()),
                row.getEmpCode(),
                format(row.getSourceRecordTime()),
                format(row.getReviewDeadline()),
                row.getReviewStatus(),
                row.getReviewResult(),
                row.getReviewOwner(),
                format(row.getReviewedAt()),
                Boolean.TRUE.equals(row.getOverdue()),
                row.getRemark());
    }

    private String normalizeListStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!LIST_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "复检状态仅支持 ALL、PENDING、IN_REVIEW、COMPLETED 或 OVERDUE");
        }
        return normalized;
    }

    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return DATE.format(LocalDate.now());
        }
        try {
            return DATE.format(LocalDate.parse(value.trim(), DATE));
        } catch (DateTimeParseException ignored) {
            throw new BusinessException(400, "复检日期格式应为 yyyy-MM-dd");
        }
    }

    private String normalizeDateTime(String value) {
        String normalized = value.trim().replace('T', ' ');
        try {
            return format(LocalDateTime.parse(normalized.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (DateTimeParseException ignored) {
            throw new BusinessException(400, "检测时间格式无效");
        }
    }

    private String cleanRemark(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
