package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionItemView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendView;
import com.xzkj.health.dto.metric.MetricAbnormalRecordPageView;
import com.xzkj.health.dto.metric.MetricDailyRiskView;
import com.xzkj.health.dto.metric.MetricDepartmentRiskView;
import com.xzkj.health.dto.metric.MetricRiskSummaryView;
import com.xzkj.health.dto.metric.MetricRiskUserPageView;
import com.xzkj.health.service.MetricPeriodRiskService;
import com.xzkj.health.service.BloodOxygenService;
import com.xzkj.health.service.metric.MetricRiskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 血氧监测控制器
 */
@RestController
@RequestMapping("/blood-oxygen")
public class BloodOxygenController {

    @Autowired
    private BloodOxygenService bloodOxygenService;

    @Autowired
    private MetricPeriodRiskService metricPeriodRiskService;

    /** 获取血氧统计概览 */
    @GetMapping("/overview")
    public Result<BloodOxygenOverviewView> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodOxygenService.getBloodOxygenStats(d[0], d[1]));
    }

    /** 获取血氧趋势数据 */
    @GetMapping("/trend")
    public Result<BloodOxygenTrendView> getTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        days = DateParamUtil.clampDays(days);
        return Result.ok("获取成功", bloodOxygenService.getBloodOxygenTrend(days));
    }

    /** 获取血氧分布数据 */
    @GetMapping("/distribution")
    public Result<List<BloodOxygenDistributionItemView>> getDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodOxygenService.getBloodOxygenDistribution(d[0], d[1]));
    }

    /** 获取 TOP N 血氧异常人员 */
    @GetMapping("/top-users")
    public Result<List<BloodOxygenTopUserView>> getTopUsers(
            @RequestParam(defaultValue = "5") Integer limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodOxygenService.getTopUsers(limit, d[0], d[1]));
    }

    /** 获取部门血氧统计 */
    @GetMapping("/department-stats")
    public Result<List<BloodOxygenDepartmentStatView>> getDepartmentStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodOxygenService.getDepartmentStats(d[0], d[1]));
    }

    /** 获取年龄段血氧统计 */
    @GetMapping("/age-stats")
    public Result<List<BloodOxygenAgeStatView>> getAgeStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodOxygenService.getAgeDistribution(d[0], d[1]));
    }

    /** 获取逐小时平均血氧（支持单日或日期范围） */
    @GetMapping("/hourly")
    public Result<List<BloodOxygenHourlyView>> getHourly(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        startDate = DateParamUtil.today(startDate);
        endDate = DateParamUtil.today(endDate);
        return Result.ok("获取成功", bloodOxygenService.getHourlyStats(startDate, endDate));
    }

    /** 实时血氧列表（近2小时） */
    @GetMapping("/realtime")
    public Result<List<BloodOxygenRealtimeView>> getRealtime(
            @RequestParam(defaultValue = "1000") Integer limit) {
        return Result.ok("获取成功", bloodOxygenService.getRealtime(limit));
    }

    @GetMapping("/risk-summary")
    public Result<MetricRiskSummaryView> getRiskSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getSummary(MetricRiskType.BLOOD_OXYGEN, d[0], d[1]));
    }

    @GetMapping("/daily-risk")
    public Result<List<MetricDailyRiskView>> getDailyRisk(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getDailyRisk(MetricRiskType.BLOOD_OXYGEN, d[0], d[1]));
    }

    @GetMapping("/department-risk")
    public Result<List<MetricDepartmentRiskView>> getDepartmentRisk(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getDepartmentRisk(MetricRiskType.BLOOD_OXYGEN, d[0], d[1]));
    }

    @GetMapping("/period-users")
    public Result<MetricRiskUserPageView> getPeriodUsers(
            @RequestParam(defaultValue = "covered") String mode,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getPeriodUsers(
                MetricRiskType.BLOOD_OXYGEN, mode, zone, d[0], d[1], Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
    }

    @GetMapping("/department-users")
    public Result<MetricRiskUserPageView> getDepartmentUsers(
            @RequestParam String dept,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getDepartmentUsers(
                MetricRiskType.BLOOD_OXYGEN, dept, d[0], d[1], Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
    }

    @GetMapping("/user-abnormal-records")
    public Result<MetricAbnormalRecordPageView> getUserAbnormalRecords(
            @RequestParam String userCode,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok("获取成功", metricPeriodRiskService.getUserAbnormalRecords(
                MetricRiskType.BLOOD_OXYGEN, userCode, startDate, endDate,
                Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
    }
}
