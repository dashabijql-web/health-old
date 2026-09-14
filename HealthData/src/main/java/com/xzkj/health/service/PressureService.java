package com.xzkj.health.service;

import com.xzkj.health.dto.pressure.PressureAbnormalPageView;
import com.xzkj.health.dto.pressure.PressureDepartmentStatView;
import com.xzkj.health.dto.pressure.PressureDistributionItemView;
import com.xzkj.health.dto.pressure.PressureHourlyView;
import com.xzkj.health.dto.pressure.PressureOverviewView;
import com.xzkj.health.dto.pressure.PressureRealtimeView;
import com.xzkj.health.dto.pressure.PressureTopUserView;
import com.xzkj.health.dto.pressure.PressureTrendView;

import java.util.List;

public interface PressureService {
    PressureOverviewView getPressureOverview(String startDate, String endDate);
    PressureTrendView getPressureTrend(int days);
    List<PressureDistributionItemView> getDistribution(String startDate, String endDate);
    List<PressureTopUserView> getTopUsers(int limit, String startDate, String endDate);
    List<PressureDepartmentStatView> getDepartmentStats(String startDate, String endDate);
    PressureAbnormalPageView getAbnormalRecords(int page, int size);
    List<PressureRealtimeView> getRealtime(int limit);
    List<PressureHourlyView> getHourlyStats(String date);
}
