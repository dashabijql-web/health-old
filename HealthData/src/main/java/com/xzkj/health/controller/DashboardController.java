package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardBodyIndicatorsView;
import com.xzkj.health.dto.dashboard.DashboardOverviewView;
import com.xzkj.health.dto.dashboard.DayBloodOxygenRankView;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.DayStepsRankView;
import com.xzkj.health.dto.dashboard.DayWarningView;
import com.xzkj.health.dto.dashboard.DeviceActivationView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptDailyPersonView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.DeptRankingView;
import com.xzkj.health.dto.dashboard.DailyAnomalyRateView;
import com.xzkj.health.dto.dashboard.DailyAbnormalStatView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.dto.dashboard.Top5UserView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.dto.dashboard.WarningEventView;
import com.xzkj.health.dto.dashboard.UnifiedControlSnapshotView;
import com.xzkj.health.dto.realtime.RealtimeOverviewView;
import com.xzkj.health.service.DashboardService;
import com.xzkj.health.service.RealtimeService;
import com.xzkj.health.service.UnifiedControlSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RealtimeService realtimeService;

    @Autowired
    private UnifiedControlSnapshotService unifiedControlSnapshotService;

    @GetMapping("/unified-control-snapshot")
    public Result<UnifiedControlSnapshotView> getUnifiedControlSnapshot(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "month") String period) {
        return Result.ok("获取成功", unifiedControlSnapshotService.getSnapshot(
                startTime, endTime, DateParamUtil.clampDays(days), groupBy, month, period));
    }

    @GetMapping("/overview")
    public Result<DashboardOverviewView> getOverview(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getCurrentMonthCounts(startTime, endTime));
    }

    @GetMapping("/body-indicators")
    public Result<DashboardBodyIndicatorsView> getBodyIndicators(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getCurrentMonthAverage(startTime, endTime));
    }

    @GetMapping("/top5")
    public Result<List<Top5UserView>> getTop5(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptTop5(startTime, endTime));
    }

    @GetMapping("/device-activation")
    public Result<DeviceActivationView> getDeviceActivation(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeviceActivation(startTime, endTime));
    }

    @GetMapping("/warning-events")
    public Result<List<WarningEventView>> getWarningEvents(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getRecentWarnings(200, startTime, endTime));
    }

    @GetMapping("/person-counts")
    public Result<PersonCountsView> getPersonCounts(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getPersonCounts(startTime, endTime));
    }

    @GetMapping("/dept-person-stats")
    public Result<List<DeptPersonStatView>> getDeptPersonStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptPersonStats(startTime, endTime));
    }

    @GetMapping("/metric-daily-detail")
    public Result<List<DailyAbnormalStatView>> getMetricDailyDetail(
            @RequestParam String metricType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getMetricDailyDetail(metricType, startTime, endTime));
    }

    @GetMapping("/dept-daily-detail")
    public Result<List<DailyAbnormalStatView>> getDeptDailyDetail(
            @RequestParam String deptName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptDailyDetail(deptName, startTime, endTime));
    }

    @GetMapping("/dept-daily-persons")
    public Result<List<DeptDailyPersonView>> getDeptDailyPersons(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptDailyPersons(startTime, endTime));
    }

    @GetMapping("/dept-stats")
    public Result<List<DeptHealthCountView>> getDeptStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptHealthCounts(startTime, endTime));
    }

    @GetMapping("/warning-counts")
    public Result<WarningDistributionView> getWarningCounts(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "day") String groupBy) {
        return Result.ok("获取成功", dashboardService.getWarningDistribution(startTime, endTime, groupBy));
    }

    @GetMapping("/daily-trend")
    public Result<List<DailyAnomalyRateView>> getDailyTrend(
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok("获取成功", dashboardService.getDailyAnomalyRates(DateParamUtil.clampDays(days)));
    }

    @GetMapping("/realtime-monitor")
    public Result<RealtimeOverviewView> getRealtimeMonitor() {
        return Result.ok("获取成功", realtimeService.getTodayAvgOverview());
    }

    @GetMapping("/user-distribution")
    public Result<List<Top5UserView>> getUserDistribution() {
        return Result.ok("获取成功", dashboardService.getDeptTop5(null, null));
    }

    @GetMapping("/health-alerts")
    public Result<List<WarningEventView>> getHealthAlerts() {
        return Result.ok("获取成功", dashboardService.getRecentWarnings(5, null, null));
    }

    @GetMapping("/health-trend")
    public Result<HealthTrendView> getHealthTrend(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok("获取成功", dashboardService.getDailyHealthTrend(DateParamUtil.clampDays(days)));
    }

    @GetMapping("/department-ranking")
    public Result<List<DeptRankingView>> getDepartmentRanking(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok("获取成功", dashboardService.getDeptRanking(startTime, endTime));
    }

    @GetMapping("/calendar")
    public Result<List<CalendarDayView>> getCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return Result.ok("获取成功", dashboardService.getCalendarData(year, month));
    }

    @GetMapping("/calendar/day-heart-rate")
    public Result<List<DayHeartRateRankView>> getDayHeartRateRank(@RequestParam String date) {
        return Result.ok("获取成功", dashboardService.getDayHeartRateRank(date));
    }

    @GetMapping("/calendar/day-blood-oxygen")
    public Result<List<DayBloodOxygenRankView>> getDayBloodOxygenRank(@RequestParam String date) {
        return Result.ok("获取成功", dashboardService.getDayBloodOxygenRank(date));
    }

    @GetMapping("/calendar/day-steps")
    public Result<List<DayStepsRankView>> getDayStepsRank(@RequestParam String date) {
        return Result.ok("获取成功", dashboardService.getDayStepsRank(date));
    }

    @GetMapping("/calendar/day-warnings")
    public Result<List<DayWarningView>> getDayWarnings(@RequestParam String date) {
        return Result.ok("获取成功", dashboardService.getDayWarnings(date));
    }

    @GetMapping("/pre-shift-compliance")
    public Result<PreShiftComplianceView> getPreShiftCompliance() {
        return Result.ok("获取成功", dashboardService.getPreShiftCompliance());
    }

    @GetMapping("/mine-entry-list")
    public Result<List<MineEntryView>> getMineEntryList(
            @RequestParam(defaultValue = "1000") int size) {
        return Result.ok("获取成功", dashboardService.getMineEntryList(size));
    }

    @GetMapping("/dept-health-comparison")
    public Result<List<DeptHealthComparisonView>> getDeptHealthComparison(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok("获取成功", dashboardService.getDeptHealthComparison(DateParamUtil.clampDays(days)));
    }
}
