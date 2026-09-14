package com.xzkj.health.service;

import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionItemView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendView;

import java.util.List;

/**
 * 血氧监测服务接口
 */
public interface BloodOxygenService {

    /**
     * 获取血氧统计概览
     */
    BloodOxygenOverviewView getBloodOxygenStats(String startDate, String endDate);

    /**
     * 获取血氧趋势数据
     * @param days 天数
     */
    BloodOxygenTrendView getBloodOxygenTrend(Integer days);

    /**
     * 获取血氧分布数据
     */
    List<BloodOxygenDistributionItemView> getBloodOxygenDistribution(String startDate, String endDate);

    /**
     * 获取TOP异常人员统计
     * @param limit 返回数量
     */
    List<BloodOxygenTopUserView> getTopUsers(Integer limit, String startDate, String endDate);

    /**
     * 获取部门血氧统计
     */
    List<BloodOxygenDepartmentStatView> getDepartmentStats(String startDate, String endDate);

    /**
     * 获取年龄段血氧分布
     */
    List<BloodOxygenAgeStatView> getAgeDistribution(String startDate, String endDate);

    /**
     * 获取指定日期每小时平均血氧
     * @param date 日期字符串 YYYY-MM-DD
     * @return List<{hour, avgBloodOxygen}>
     */
    List<BloodOxygenHourlyView> getHourlyStats(String startDate, String endDate);

    List<BloodOxygenRealtimeView> getRealtime(int limit);
}
