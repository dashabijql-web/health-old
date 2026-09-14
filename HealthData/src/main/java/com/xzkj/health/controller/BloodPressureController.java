package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalPageView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionItemView;
import com.xzkj.health.dto.bloodpressure.BloodPressureHourlyView;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendView;
import com.xzkj.health.dto.metric.MetricAbnormalRecordPageView;
import com.xzkj.health.dto.metric.MetricDailyRiskView;
import com.xzkj.health.dto.metric.MetricDepartmentRiskView;
import com.xzkj.health.dto.metric.MetricRiskSummaryView;
import com.xzkj.health.dto.metric.MetricRiskUserPageView;
import com.xzkj.health.service.MetricPeriodRiskService;
import com.xzkj.health.service.BloodPressureService;
import com.xzkj.health.service.metric.MetricRiskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 血压分析控制器
 */
@RestController
@RequestMapping("/blood-pressure")
public class BloodPressureController {

    @Autowired
    private BloodPressureService bloodPressureService;

    @Autowired
    private MetricPeriodRiskService metricPeriodRiskService;

    /** 概览统计 */
    @GetMapping("/overview")
    public Result<BloodPressureOverviewView> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodPressureService.getBloodPressureOverview(d[0], d[1]));
    }

    /** 趋势折线图（收缩压 + 舒张压） */
    @GetMapping("/trend")
    public Result<BloodPressureTrendView> getTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        days = DateParamUtil.clampDays(days);
        return Result.ok("获取成功", bloodPressureService.getBloodPressureTrend(days));
    }

    /** 分布饼图 */
    @GetMapping("/distribution")
    public Result<List<BloodPressureDistributionItemView>> getDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodPressureService.getDistribution(d[0], d[1]));
    }

    /** TOP N 高血压人员 */
    @GetMapping("/top-users")
    public Result<List<BloodPressureTopUserView>> getTopUsers(
            @RequestParam(defaultValue = "5") Integer limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodPressureService.getTopUsers(limit, d[0], d[1]));
    }

    /** 部门血压统计 */
    @GetMapping("/department-stats")
    public Result<List<BloodPressureDepartmentStatView>> getDepartmentStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", bloodPressureService.getDepartmentStats(d[0], d[1]));
    }

    /** 实时血压列表 */
    @GetMapping("/realtime")
    public Result<List<BloodPressureRealtimeView>> getRealtime(
            @RequestParam(defaultValue = "1000") Integer limit) {
        return Result.ok("获取成功", bloodPressureService.getRealtime(limit));
    }

    /** 指定日期逐小时均值 */
    @GetMapping("/hourly")
    public Result<List<BloodPressureHourlyView>> getHourly(
            @RequestParam(required = false) String date) {
        return Result.ok("获取成功", bloodPressureService.getHourlyStats(DateParamUtil.today(date)));
    }

    /** 异常记录分页 */
    @GetMapping("/abnormal")
    public Result<BloodPressureAbnormalPageView> getAbnormal(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        size = DateParamUtil.clampSize(size);
        return Result.ok("获取成功", bloodPressureService.getAbnormalRecords(page, size));
    }

    @GetMapping("/risk-summary")
    public Result<MetricRiskSummaryView> getRiskSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getSummary(MetricRiskType.BLOOD_PRESSURE, d[0], d[1]));
    }

    @GetMapping("/daily-risk")
    public Result<List<MetricDailyRiskView>> getDailyRisk(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getDailyRisk(MetricRiskType.BLOOD_PRESSURE, d[0], d[1]));
    }

    @GetMapping("/department-risk")
    public Result<List<MetricDepartmentRiskView>> getDepartmentRisk(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", metricPeriodRiskService.getDepartmentRisk(MetricRiskType.BLOOD_PRESSURE, d[0], d[1]));
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
                MetricRiskType.BLOOD_PRESSURE, mode, zone, d[0], d[1], Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
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
                MetricRiskType.BLOOD_PRESSURE, dept, d[0], d[1], Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
    }

    @GetMapping("/user-abnormal-records")
    public Result<MetricAbnormalRecordPageView> getUserAbnormalRecords(
            @RequestParam String userCode,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok("获取成功", metricPeriodRiskService.getUserAbnormalRecords(
                MetricRiskType.BLOOD_PRESSURE, userCode, startDate, endDate,
                Math.max(1, page), Math.max(1, DateParamUtil.clampSize(size))));
    }
}
