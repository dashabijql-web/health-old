package com.xzkj.health.service;

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

import java.util.List;

/**
 * 心率监测服务接口
 */
public interface HeartRateService {

    HeartRateOverviewView getHeartRateOverview(String startDate, String endDate);

    List<HeartRateTopUserView> getTopUsers(int limit, String startDate, String endDate);

    List<HeartRateAgeStatView> getAgeDistribution(String startDate, String endDate);

    List<HeartRateDistributionItemView> getHeartRateDistribution(String startDate, String endDate);

    HeartRateTrendView getHeartRateTrend(int days);

    List<HeartRateDepartmentStatView> getDepartmentStats(String startDate, String endDate);

    List<HeartRateHourlyView> getHourlyStats(String startDate, String endDate);

    List<HeartRateRealtimeView> getRealtime(int limit);

    List<HeartRateDailyAnomalyView> getDailyAnomalyCount(String startDate, String endDate);

    HeartRateDepartmentUserPageView getDepartmentAbnormalUsers(
            String deptName, String startDate, String endDate, int page, int size);

    HeartRatePeriodUserPageView getPeriodUsers(
            String mode, String zone, String startDate, String endDate, int page, int size);

    HeartRateAbnormalRecordPageView getUserAbnormalRecords(
            String userCode, String startDate, String endDate, int page, int size);
}
