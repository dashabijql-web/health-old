package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.dto.heartrate.HeartRateAgeStatView;
import com.xzkj.health.dto.heartrate.HeartRateAbnormalRecordPageView;
import com.xzkj.health.dto.heartrate.HeartRateDailyAnomalyView;
import com.xzkj.health.dto.heartrate.HeartRateDepartmentStatView;
import com.xzkj.health.dto.heartrate.HeartRateDepartmentUserPageView;
import com.xzkj.health.dto.heartrate.HeartRateDistributionItemView;
import com.xzkj.health.dto.heartrate.HeartRateHourlyView;
import com.xzkj.health.dto.heartrate.HeartRateOverviewView;
import com.xzkj.health.dto.heartrate.HeartRatePeriodUserPageView;
import com.xzkj.health.dto.heartrate.HeartRateRealtimeView;
import com.xzkj.health.dto.heartrate.HeartRateTopUserView;
import com.xzkj.health.dto.heartrate.HeartRateTrendView;
import com.xzkj.health.service.HeartRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 心率监测控制器
 */
@RestController
@RequestMapping("/heart-rate")
public class HeartRateController {

    @Autowired
    private HeartRateService heartRateService;

    /** 获取心率统计概览 */
    @GetMapping("/overview")
    public Result<HeartRateOverviewView> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getHeartRateOverview(d[0], d[1]));
    }

    /** 获取 TOP N 心率异常人员 */
    @GetMapping("/top-users")
    public Result<List<HeartRateTopUserView>> getTopUsers(
            @RequestParam(defaultValue = "5") Integer limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getTopUsers(limit, d[0], d[1]));
    }

    /** 获取年龄段心率统计 */
    @GetMapping("/age-stats")
    public Result<List<HeartRateAgeStatView>> getAgeStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getAgeDistribution(d[0], d[1]));
    }

    /** 获取心率分布统计（偏低/正常/偏高） */
    @GetMapping("/distribution")
    public Result<List<HeartRateDistributionItemView>> getDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getHeartRateDistribution(d[0], d[1]));
    }

    /** 获取心率趋势数据 */
    @GetMapping("/trend")
    public Result<HeartRateTrendView> getTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        days = DateParamUtil.clampDays(days);
        return Result.ok("获取成功", heartRateService.getHeartRateTrend(days));
    }

    /** 获取部门心率统计 */
    @GetMapping("/department-stats")
    public Result<List<HeartRateDepartmentStatView>> getDepartmentStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getDepartmentStats(d[0], d[1]));
    }

    /** 获取逐小时平均心率（支持单日或日期范围） */
    @GetMapping("/hourly")
    public Result<List<HeartRateHourlyView>> getHourly(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        startDate = DateParamUtil.today(startDate);
        endDate = DateParamUtil.today(endDate);
        return Result.ok("获取成功", heartRateService.getHourlyStats(startDate, endDate));
    }

    /** 实时心率列表（近2小时） */
    @GetMapping("/realtime")
    public Result<List<HeartRateRealtimeView>> getRealtime(
            @RequestParam(defaultValue = "1000") Integer limit) {
        return Result.ok("获取成功", heartRateService.getRealtime(limit));
    }

    /** 按日统计心率异常人次 */
    @GetMapping("/daily-anomaly")
    public Result<List<HeartRateDailyAnomalyView>> getDailyAnomaly(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", heartRateService.getDailyAnomalyCount(d[0], d[1]));
    }

    /** 点击部门异常柱状图后的周期人员下钻。 */
    @GetMapping("/department-users")
    public Result<HeartRateDepartmentUserPageView> getDepartmentUsers(
            @RequestParam String dept,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        page = Math.max(1, page);
        size = DateParamUtil.clampSize(size);
        return Result.ok("获取成功", heartRateService.getDepartmentAbnormalUsers(
                dept, d[0], d[1], page, size));
    }

    /** 顶部异常人数/覆盖人数的周期人员证据。 */
    @GetMapping("/period-users")
    public Result<HeartRatePeriodUserPageView> getPeriodUsers(
            @RequestParam(defaultValue = "covered") String mode,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        page = Math.max(1, page);
        size = Math.max(1, DateParamUtil.clampSize(size));
        String safeMode = "abnormal".equalsIgnoreCase(mode) ? "abnormal" : "covered";
        return Result.ok("获取成功", heartRateService.getPeriodUsers(
                safeMode, zone, d[0], d[1], page, size));
    }

    /** 从周期异常人员进入健康画像后的心率异常明细。 */
    @GetMapping("/user-abnormal-records")
    public Result<HeartRateAbnormalRecordPageView> getUserAbnormalRecords(
            @RequestParam String userCode,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        page = Math.max(1, page);
        size = Math.max(1, DateParamUtil.clampSize(size));
        return Result.ok("获取成功", heartRateService.getUserAbnormalRecords(
                userCode, startDate, endDate, page, size));
    }
}
