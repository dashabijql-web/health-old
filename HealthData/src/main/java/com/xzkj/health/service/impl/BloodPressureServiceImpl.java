package com.xzkj.health.service.impl;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalPageView;
import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalRecordView;
import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalRecordRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionItemView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureHourlyView;
import com.xzkj.health.dto.bloodpressure.BloodPressureHourlyRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendView;
import com.xzkj.health.mapper.BloodPressureMapper;
import com.xzkj.health.service.BloodPressureService;
import com.xzkj.health.util.LocalTtlCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 血压监测服务实现
 * 异常由 GlobalExceptionHandler 统一处理
 */
@Service
public class BloodPressureServiceImpl implements BloodPressureService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    @Autowired
    private BloodPressureMapper bloodPressureMapper;

    private final LocalTtlCache<BloodPressureOverviewView> overviewCache = new LocalTtlCache<>();
    private final LocalTtlCache<BloodPressureTrendView> trendCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<BloodPressureDepartmentStatView>> departmentStatsCache = new LocalTtlCache<>();

    @Override
    public BloodPressureOverviewView getBloodPressureOverview(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("overview", startDate, endDate);
        BloodPressureOverviewView hit = overviewCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        BloodPressureOverviewView result = toOverviewView(bloodPressureMapper.getOverview(startDate, endDate));
        overviewCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public BloodPressureTrendView getBloodPressureTrend(int days) {
        String cacheKey = HealthCacheKeys.key("trend", days);
        BloodPressureTrendView hit = trendCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        BloodPressureTrendView result = toTrendView(bloodPressureMapper.getTrend(days));
        trendCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<BloodPressureDistributionItemView> getDistribution(String startDate, String endDate) {
        return toDistributionViews(bloodPressureMapper.getDistribution(startDate, endDate));
    }

    @Override
    public List<BloodPressureTopUserView> getTopUsers(int limit, String startDate, String endDate) {
        return toTopUserViews(bloodPressureMapper.getTopUsers(limit, startDate, endDate));
    }

    @Override
    public List<BloodPressureDepartmentStatView> getDepartmentStats(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("department-stats", startDate, endDate);
        List<BloodPressureDepartmentStatView> hit = departmentStatsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<BloodPressureDepartmentStatView> result = toDepartmentStatViews(bloodPressureMapper.getDepartmentStats(startDate, endDate));
        departmentStatsCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<BloodPressureRealtimeView> getRealtime(int limit) {
        return toRealtimeViews(bloodPressureMapper.getRealtime(limit));
    }

    @Override
    public List<BloodPressureHourlyView> getHourlyStats(String date) {
        return toHourlyViews(bloodPressureMapper.getHourlyStats(date));
    }

    @Override
    public BloodPressureAbnormalPageView getAbnormalRecords(int page, int size) {
        int offset = (page - 1) * size;
        return new BloodPressureAbnormalPageView(
                toAbnormalRecordViews(bloodPressureMapper.getAbnormalRecords(offset, size)),
                bloodPressureMapper.countAbnormalRecords(),
                page,
                size
        );
    }

    private BloodPressureOverviewView toOverviewView(BloodPressureOverviewRow row) {
        return new BloodPressureOverviewView(
                intValue(row == null ? null : row.getAvgSystolic()),
                intValue(row == null ? null : row.getAvgDiastolic()),
                intValue(row == null ? null : row.getMinSystolic()),
                intValue(row == null ? null : row.getMaxSystolic()),
                intValue(row == null ? null : row.getMinDiastolic()),
                intValue(row == null ? null : row.getMaxDiastolic()),
                intValue(row == null ? null : row.getDetectionCount()),
                intValue(row == null ? null : row.getTotalCount()),
                intValue(row == null ? null : row.getNormalRate()),
                intValue(row == null ? null : row.getAbnormalCount()),
                intValue(row == null ? null : row.getElevatedRate()),
                intValue(row == null ? null : row.getHypertensionRate())
        );
    }

    private BloodPressureTrendView toTrendView(List<BloodPressureTrendRow> rows) {
        List<String> dates = new ArrayList<>();
        List<Integer> systolicValues = new ArrayList<>();
        List<Integer> diastolicValues = new ArrayList<>();
        for (BloodPressureTrendRow row : orEmpty(rows)) {
            dates.add(DateParamUtil.shortDate(stringValue(row.getDate())));
            systolicValues.add(intValue(row.getAvgSystolic()));
            diastolicValues.add(intValue(row.getAvgDiastolic()));
        }
        return new BloodPressureTrendView(dates, systolicValues, diastolicValues);
    }

    private List<BloodPressureDistributionItemView> toDistributionViews(List<BloodPressureDistributionRow> rows) {
        List<BloodPressureDistributionItemView> result = new ArrayList<>();
        for (BloodPressureDistributionRow row : orEmpty(rows)) {
            result.add(new BloodPressureDistributionItemView(
                    stringValue(row.getName()),
                    intValue(row.getValue()),
                    stringValue(row.getColor())
            ));
        }
        return result;
    }

    private List<BloodPressureTopUserView> toTopUserViews(List<BloodPressureTopUserRow> rows) {
        List<BloodPressureTopUserView> result = new ArrayList<>();
        for (BloodPressureTopUserRow row : orEmpty(rows)) {
            result.add(new BloodPressureTopUserView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    intValue(row.getAvgSystolic()),
                    intValue(row.getAvgDiastolic()),
                    intValue(row.getCount())
            ));
        }
        return result;
    }

    private List<BloodPressureDepartmentStatView> toDepartmentStatViews(List<BloodPressureDepartmentStatRow> rows) {
        List<BloodPressureDepartmentStatView> result = new ArrayList<>();
        for (BloodPressureDepartmentStatRow row : orEmpty(rows)) {
            result.add(new BloodPressureDepartmentStatView(
                    stringValue(row.getDeptName()),
                    intValue(row.getAvgSystolic()),
                    intValue(row.getAvgDiastolic()),
                    intValue(row.getAbnormalCount()),
                    intValue(row.getTotalCount())
            ));
        }
        return result;
    }

    private List<BloodPressureRealtimeView> toRealtimeViews(List<BloodPressureRealtimeRow> rows) {
        List<BloodPressureRealtimeView> result = new ArrayList<>();
        for (BloodPressureRealtimeRow row : orEmpty(rows)) {
            result.add(new BloodPressureRealtimeView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    nullableInt(row.getSystolic()),
                    nullableInt(row.getDiastolic()),
                    stringValue(row.getRecordTime())
            ));
        }
        return result;
    }

    private List<BloodPressureHourlyView> toHourlyViews(List<BloodPressureHourlyRow> rows) {
        List<BloodPressureHourlyView> result = new ArrayList<>();
        for (BloodPressureHourlyRow row : orEmpty(rows)) {
            if (row == null) {
                continue;
            }
            result.add(new BloodPressureHourlyView(
                    intValue(row.getHour()),
                    intValue(row.getAvgSystolic()),
                    intValue(row.getAvgDiastolic())
            ));
        }
        return result;
    }

    private List<BloodPressureAbnormalRecordView> toAbnormalRecordViews(List<BloodPressureAbnormalRecordRow> rows) {
        List<BloodPressureAbnormalRecordView> result = new ArrayList<>();
        for (BloodPressureAbnormalRecordRow row : orEmpty(rows)) {
            result.add(new BloodPressureAbnormalRecordView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    nullableInt(row.getSystolic()),
                    nullableInt(row.getDiastolic()),
                    stringValue(row.getLevel()),
                    stringValue(row.getRecordTime())
            ));
        }
        return result;
    }

    private <T> List<T> orEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer nullableInt(Number value) {
        return value == null ? null : value.intValue();
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
