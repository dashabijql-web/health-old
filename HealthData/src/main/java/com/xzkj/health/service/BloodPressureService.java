package com.xzkj.health.service;

import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalPageView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionItemView;
import com.xzkj.health.dto.bloodpressure.BloodPressureHourlyView;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendView;

import java.util.List;

public interface BloodPressureService {
    BloodPressureOverviewView getBloodPressureOverview(String startDate, String endDate);
    BloodPressureTrendView getBloodPressureTrend(int days);
    List<BloodPressureDistributionItemView> getDistribution(String startDate, String endDate);
    List<BloodPressureTopUserView> getTopUsers(int limit, String startDate, String endDate);
    List<BloodPressureDepartmentStatView> getDepartmentStats(String startDate, String endDate);
    BloodPressureAbnormalPageView getAbnormalRecords(int page, int size);
    List<BloodPressureRealtimeView> getRealtime(int limit);
    List<BloodPressureHourlyView> getHourlyStats(String date);
}
