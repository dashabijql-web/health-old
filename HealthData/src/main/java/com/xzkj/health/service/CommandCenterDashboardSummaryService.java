package com.xzkj.health.service;

import com.xzkj.health.dto.commandcenter.CommandCenterDashboardSummaryView;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryView;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.mapper.CommandCenterIncidentMapper;
import com.xzkj.health.util.LocalTtlCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommandCenterDashboardSummaryService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long SUMMARY_TTL_MS = 15_000L;

    private final LocalTtlCache<CommandCenterDashboardSummaryView> summaryCache = new LocalTtlCache<>();
    private final RiskWarningService riskWarningService;
    private final DashboardService dashboardService;
    private final CommandCenterIncidentMapper incidentMapper;
    private final PreShiftReviewService preShiftReviewService;
    private final DeviceOperationalService deviceOperationalService;

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public CommandCenterDashboardSummaryView getSummary(String requestedPeriod) {
        String period = normalizePeriod(requestedPeriod);
        return summaryCache.getOrLoad(period, SUMMARY_TTL_MS, () -> loadSummary(period));
    }

    private CommandCenterDashboardSummaryView loadSummary(String period) {
        LocalDate today = LocalDate.now();
        String startAt = today.atStartOfDay().format(DATE_TIME);
        String endAt = today.plusDays(1).atStartOfDay().format(DATE_TIME);
        int periodDays = switch (period) {
            case "week" -> 7;
            case "month" -> 30;
            default -> 1;
        };
        String periodStartAt = today.minusDays(periodDays - 1L).atStartOfDay().format(DATE_TIME);

        int todayNew = countWarnings(null, null, startAt, endAt);
        int periodNew = periodDays == 1
                ? todayNew
                : countWarnings(null, null, periodStartAt, endAt);
        int pendingTotal = countWarnings(null, false, startAt, endAt);
        int criticalPending = countWarnings("高危", false, startAt, endAt);
        Map<String, Object> workflow = incidentMapper.getOpenWorkflowSummary(startAt, endAt);
        int assignedOpen = intValue(workflow == null ? null : workflow.get("assignedOpen"));
        int overdueOpen = intValue(workflow == null ? null : workflow.get("overdueOpen"));

        PreShiftComplianceView admission = dashboardService.getPreShiftCompliance();
        PreShiftReviewSummaryView reviews = preShiftReviewService.getTodaySummary();
        DeviceOperationalSummaryView devices = deviceOperationalService.getSummary();

        return new CommandCenterDashboardSummaryView(
                new CommandCenterDashboardSummaryView.WarningSummary(
                        todayNew,
                        periodNew,
                        period,
                        criticalPending,
                        pendingTotal,
                        Math.max(0, pendingTotal - assignedOpen),
                        overdueOpen),
                new CommandCenterDashboardSummaryView.AdmissionSummary(
                        admission.qualifiedCount(),
                        admission.failedCount(),
                        CommandCenterDashboardSummaryView.CapabilityValue.available(
                                reviews.awaitingReview(), "待复检任务来自班前异常检测"),
                        CommandCenterDashboardSummaryView.CapabilityValue.available(
                                reviews.retestOverdue(), "已超过复检时限")),
                new CommandCenterDashboardSummaryView.DeviceSummary(
                        devices.total(),
                        devices.online(),
                        devices.offline(),
                        devices.onlineRate(),
                        CommandCenterDashboardSummaryView.CapabilityValue.available(
                                devices.lowBattery(), "电量低于配置阈值"),
                        CommandCenterDashboardSummaryView.CapabilityValue.available(
                                devices.dataInterrupted(), "在线但超过上报时限"),
                        CommandCenterDashboardSummaryView.CapabilityValue.available(
                                devices.faulted(), "人工登记且尚未关闭的设备故障")),
                LocalDateTime.now().format(DATE_TIME));
    }

    private int countWarnings(String level, Boolean handled, String startAt, String endAt) {
        return riskWarningService.countWarningsByTimeWindow(level, handled, startAt, endAt);
    }

    private String normalizePeriod(String period) {
        return "week".equals(period) || "month".equals(period) ? period : "day";
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
