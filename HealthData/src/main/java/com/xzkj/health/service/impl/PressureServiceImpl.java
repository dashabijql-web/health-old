package com.xzkj.health.service.impl;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.pressure.PressureAbnormalPageView;
import com.xzkj.health.dto.pressure.PressureAbnormalRecordView;
import com.xzkj.health.dto.pressure.PressureAbnormalRecordRow;
import com.xzkj.health.dto.pressure.PressureDepartmentStatView;
import com.xzkj.health.dto.pressure.PressureDepartmentStatRow;
import com.xzkj.health.dto.pressure.PressureDistributionItemView;
import com.xzkj.health.dto.pressure.PressureDistributionRow;
import com.xzkj.health.dto.pressure.PressureHourlyView;
import com.xzkj.health.dto.pressure.PressureHourlyRow;
import com.xzkj.health.dto.pressure.PressureOverviewView;
import com.xzkj.health.dto.pressure.PressureOverviewRow;
import com.xzkj.health.dto.pressure.PressureRealtimeView;
import com.xzkj.health.dto.pressure.PressureRealtimeRow;
import com.xzkj.health.dto.pressure.PressureTopUserView;
import com.xzkj.health.dto.pressure.PressureTopUserRow;
import com.xzkj.health.dto.pressure.PressureTrendRow;
import com.xzkj.health.dto.pressure.PressureTrendView;
import com.xzkj.health.mapper.PressureMapper;
import com.xzkj.health.service.PressureService;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 压力监测服务实现
 * 异常由 GlobalExceptionHandler 统一处理
 */
@Service
public class PressureServiceImpl implements PressureService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    @Autowired
    private PressureMapper pressureMapper;

    private final LocalTtlCache<PressureOverviewView> overviewCache = new LocalTtlCache<>();
    private final LocalTtlCache<PressureTrendView> trendCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<PressureDepartmentStatView>> departmentStatsCache = new LocalTtlCache<>();

    @Override
    public PressureOverviewView getPressureOverview(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("overview", startDate, endDate);
        PressureOverviewView hit = overviewCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        PressureOverviewView result = toOverviewView(pressureMapper.getOverview(startDate, endDate));
        overviewCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public PressureTrendView getPressureTrend(int days) {
        String cacheKey = HealthCacheKeys.key("trend", days);
        PressureTrendView hit = trendCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        PressureTrendView result = toTrendView(pressureMapper.getTrend(days));
        trendCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<PressureDistributionItemView> getDistribution(String startDate, String endDate) {
        String tableSource = TableSourceUtil.healthRecordSource(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                "user_code,record_time,pressure");
        if (tableSource.startsWith("(")) {
            tableSource = tableSource + " AS _pr";
        }
        return toDistributionViews(pressureMapper.getDistributionDirect(tableSource, startDate, endDate));
    }

    @Override
    public List<PressureTopUserView> getTopUsers(int limit, String startDate, String endDate) {
        return toTopUserViews(pressureMapper.getTopUsers(limit, startDate, endDate));
    }

    @Override
    public List<PressureDepartmentStatView> getDepartmentStats(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("department-stats", startDate, endDate);
        List<PressureDepartmentStatView> hit = departmentStatsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<PressureDepartmentStatView> result = toDepartmentStatViews(pressureMapper.getDepartmentStats(startDate, endDate));
        departmentStatsCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<PressureRealtimeView> getRealtime(int limit) {
        return toRealtimeViews(pressureMapper.getRealtime(limit));
    }

    @Override
    public List<PressureHourlyView> getHourlyStats(String date) {
        return toHourlyViews(pressureMapper.getHourlyStats(date));
    }

    @Override
    public PressureAbnormalPageView getAbnormalRecords(int page, int size) {
        int offset = (page - 1) * size;
        return new PressureAbnormalPageView(
                toAbnormalRecordViews(pressureMapper.getAbnormalRecords(offset, size)),
                pressureMapper.countAbnormalRecords(),
                page,
                size
        );
    }

    private PressureOverviewView toOverviewView(PressureOverviewRow row) {
        return new PressureOverviewView(
                intValue(row == null ? null : row.getAvgPressure()),
                intValue(row == null ? null : row.getMinPressure()),
                intValue(row == null ? null : row.getMaxPressure()),
                intValue(row == null ? null : row.getDetectionCount()),
                intValue(row == null ? null : row.getTotalCount()),
                intValue(row == null ? null : row.getNormalRate()),
                intValue(row == null ? null : row.getAbnormalCount()),
                intValue(row == null ? null : row.getHighCount())
        );
    }

    private PressureTrendView toTrendView(List<PressureTrendRow> rows) {
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (PressureTrendRow row : orEmpty(rows)) {
            dates.add(DateParamUtil.shortDate(stringValue(row.getDate())));
            values.add(intValue(row.getAvgPressure()));
        }
        return new PressureTrendView(dates, values);
    }

    private List<PressureDistributionItemView> toDistributionViews(List<PressureDistributionRow> rows) {
        List<PressureDistributionItemView> result = new ArrayList<>();
        for (PressureDistributionRow row : orEmpty(rows)) {
            result.add(new PressureDistributionItemView(
                    stringValue(row.getName()),
                    intValue(row.getValue()),
                    stringValue(row.getColor())
            ));
        }
        return result;
    }

    private List<PressureTopUserView> toTopUserViews(List<PressureTopUserRow> rows) {
        List<PressureTopUserView> result = new ArrayList<>();
        for (PressureTopUserRow row : orEmpty(rows)) {
            result.add(new PressureTopUserView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    intValue(row.getAvgPressure()),
                    intValue(row.getMaxPressure()),
                    intValue(row.getCount())
            ));
        }
        return result;
    }

    private List<PressureDepartmentStatView> toDepartmentStatViews(List<PressureDepartmentStatRow> rows) {
        List<PressureDepartmentStatView> result = new ArrayList<>();
        for (PressureDepartmentStatRow row : orEmpty(rows)) {
            result.add(new PressureDepartmentStatView(
                    stringValue(row.getDeptName()),
                    intValue(row.getAvgPressure()),
                    intValue(row.getHighCount()),
                    intValue(row.getAbnormalCount()),
                    intValue(row.getTotalCount())
            ));
        }
        return result;
    }

    private List<PressureRealtimeView> toRealtimeViews(List<PressureRealtimeRow> rows) {
        List<PressureRealtimeView> result = new ArrayList<>();
        for (PressureRealtimeRow row : orEmpty(rows)) {
            result.add(new PressureRealtimeView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    nullableInt(row.getPressure()),
                    stringValue(row.getRecordTime())
            ));
        }
        return result;
    }

    private List<PressureHourlyView> toHourlyViews(List<PressureHourlyRow> rows) {
        List<PressureHourlyView> result = new ArrayList<>();
        for (PressureHourlyRow row : orEmpty(rows)) {
            if (row == null) {
                continue;
            }
            result.add(new PressureHourlyView(
                    intValue(row.getHour()),
                    intValue(row.getAvgPressure())
            ));
        }
        return result;
    }

    private List<PressureAbnormalRecordView> toAbnormalRecordViews(List<PressureAbnormalRecordRow> rows) {
        List<PressureAbnormalRecordView> result = new ArrayList<>();
        for (PressureAbnormalRecordRow row : orEmpty(rows)) {
            result.add(new PressureAbnormalRecordView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    nullableInt(row.getPressure()),
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
