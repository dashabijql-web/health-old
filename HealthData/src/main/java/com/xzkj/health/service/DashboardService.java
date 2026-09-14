package com.xzkj.health.service;

import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardBodyIndicatorsView;
import com.xzkj.health.dto.dashboard.DashboardOverviewView;
import com.xzkj.health.dto.dashboard.DayBloodOxygenRankView;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.DayStepsRankView;
import com.xzkj.health.dto.dashboard.DayWarningView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptRankingView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.dto.dashboard.DeviceActivationView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptDailyPersonView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.DailyAnomalyRateView;
import com.xzkj.health.dto.dashboard.DailyAbnormalStatView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.Top5UserView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.dto.dashboard.WarningEventView;

import java.util.List;

/**
 * Dashboard服务接口
 */
public interface DashboardService {

    /**
     * 获取指定时间段内的检测人数统计
     * @param startTime 开始日期（YYYY-MM-DD），null 则默认当月月初
     * @param endTime   结束日期（YYYY-MM-DD），null 则默认当月月末
     */
    DashboardOverviewView getCurrentMonthCounts(String startTime, String endTime);

    /**
     * 获取指定时间段内的平均身体指标
     */
    DashboardBodyIndicatorsView getCurrentMonthAverage(String startTime, String endTime);

    /**
     * 获取指定时间段内预警次数最多的员工 TOP15
     */
    List<Top5UserView> getDeptTop5(String startTime, String endTime);

    /**
     * 获取设备激活面板数据（统计 + 预警率）
     */
    DeviceActivationView getDeviceActivation(String startTime, String endTime);

    /**
     * 获取指定时间段内的最近预警事件
     * @param limit     返回数量
     */
    List<WarningEventView> getRecentWarnings(int limit, String startTime, String endTime);

    /**
     * 按部门统计指定时间段内的健康数据量
     */
    List<DeptHealthCountView> getDeptHealthCounts(String startTime, String endTime);

    /**
     * 各指标检测人数（DISTINCT 人数）
     */
    PersonCountsView getPersonCounts(String startTime, String endTime);

    /**
     * 各部门检测人数 + 异常人数
     */
    List<DeptPersonStatView> getDeptPersonStats(String startTime, String endTime);

    /**
     * 指标每日检测人数 + 异常人数
     */
    List<DailyAbnormalStatView> getMetricDailyDetail(String metricType, String startTime, String endTime);

    /**
     * 单部门每日检测人数 + 异常人数
     */
    List<DailyAbnormalStatView> getDeptDailyDetail(String deptName, String startTime, String endTime);

    /**
     * 各部门每日检测人数
     */
    List<DeptDailyPersonView> getDeptDailyPersons(String startTime, String endTime);

    /**
     * 获取过去 N 天每日各指标异常率
     */
    List<DailyAnomalyRateView> getDailyAnomalyRates(int days);

    /**
     * 按日/时统计预警数量（柱状图专用，不受 TOP 10000 限制）
     * groupBy: "day" 或 "hour"（hour 仅对当日有效）
     */
    WarningDistributionView getWarningDistribution(String startTime, String endTime, String groupBy);

    /**
     * 获取过去 N 天每日体征均值（心率/血氧/步数），用于健康趋势图
     */
    HealthTrendView getDailyHealthTrend(int days);

    /**
     * 获取部门健康排名（基于真实预警率），默认当月
     */
    List<DeptRankingView> getDeptRanking(String startTime, String endTime);

    /**
     * 工作台日历数据
     */
    List<CalendarDayView> getCalendarData(Integer year, Integer month);

    /**
     * 今日班前健康达标率
     */
    PreShiftComplianceView getPreShiftCompliance();

    /**
     * 今日入井准入名单
     */
    List<MineEntryView> getMineEntryList(int size);

    /**
     * 部门健康对比
     */
    List<DeptHealthComparisonView> getDeptHealthComparison(int days);

    /** 指定日期心率排行（偏差最大排前） */
    List<DayHeartRateRankView> getDayHeartRateRank(String date);

    /** 指定日期血氧排行（最低排前） */
    List<DayBloodOxygenRankView> getDayBloodOxygenRank(String date);

    /** 指定日期步数排行（最少排前） */
    List<DayStepsRankView> getDayStepsRank(String date);

    /** 指定日期预警列表（最严重排前） */
    List<DayWarningView> getDayWarnings(String date);
}
