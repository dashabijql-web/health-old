package com.xzkj.health.service.impl;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionItemView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendView;
import com.xzkj.health.mapper.BloodOxygenMapper;
import com.xzkj.health.service.BloodOxygenService;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 血氧监测服务实现
 * 异常由 GlobalExceptionHandler 统一处理
 */
@Service
public class BloodOxygenServiceImpl implements BloodOxygenService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    @Autowired
    private BloodOxygenMapper bloodOxygenMapper;

    private final LocalTtlCache<BloodOxygenOverviewView> overviewCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<BloodOxygenDepartmentStatView>> departmentStatsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<BloodOxygenAgeStatView>> ageStatsCache = new LocalTtlCache<>();
    private final LocalTtlCache<BloodOxygenTrendView> trendCache = new LocalTtlCache<>();

    /** 根据日期范围路由分区表（单月=表名，跨月=UNION ALL裸子查询不含别名）
     * 不加 AS 末尾别名 — Mapper SQL 中 FROM ${tableSource} AS _bo 自行提供别名
     */
    private String healthSource(String start, String end) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "user_code,record_time,blood_oxygen"
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public BloodOxygenOverviewView getBloodOxygenStats(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("overview", startDate, endDate);
        BloodOxygenOverviewView hit = overviewCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        // 优化（Round 15）：路由到分区表，避免 v_health_record UNION ALL 全扫描（6s→预期<500ms）
        String tblSrc = healthSource(startDate, endDate);
        BloodOxygenOverviewView result = toOverviewView(
                bloodOxygenMapper.getBloodOxygenStatsDirect(tblSrc, startDate, endDate)
        );
        overviewCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public BloodOxygenTrendView getBloodOxygenTrend(Integer days) {
        String cacheKey = HealthCacheKeys.key("trend", days);
        BloodOxygenTrendView hit = trendCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        BloodOxygenTrendView result = toTrendView(bloodOxygenMapper.getBloodOxygenTrend(days));
        trendCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<BloodOxygenDistributionItemView> getBloodOxygenDistribution(String startDate, String endDate) {
        List<BloodOxygenDistributionRow> rawData = bloodOxygenMapper.getBloodOxygenDistribution(startDate, endDate);

        // 计算总数
        int total = 0;
        int lowCount = 0;
        int normalCount = 0;
        int highCount = 0;

        if (rawData != null && !rawData.isEmpty()) {
            for (BloodOxygenDistributionRow item : rawData) {
                String range = item.getRange();
                int count = intValue(item.getCount());
                total += count;

                if ("<90".equals(range)) {
                    lowCount += count;
                } else if ("≥99".equals(range)) {
                    highCount += count;
                } else {
                    normalCount += count;
                }
            }
        }

        // 转换为前端需要的格式
        List<BloodOxygenDistributionItemView> result = new ArrayList<>();

        if (total > 0) {
            // 血氧偏低 (<90%)
            if (lowCount > 0) {
                result.add(new BloodOxygenDistributionItemView(
                        "血氧偏低",
                        (int) Math.round(lowCount * 100.0 / total),
                        "#4FC3F7"
                ));
            }

            // 血氧正常 (90~100%): normalCount(90-99) + highCount(≥99，实为正常优秀)
            int allNormal = normalCount + highCount;
            if (allNormal > 0) {
                result.add(new BloodOxygenDistributionItemView(
                        "血氧正常",
                        (int) Math.round(allNormal * 100.0 / total),
                        "#38ef7d"
                ));
            }
        }

        return result;
    }

    @Override
    public List<BloodOxygenTopUserView> getTopUsers(Integer limit, String startDate, String endDate) {
        List<BloodOxygenTopUserView> result = new ArrayList<>();
        for (BloodOxygenTopUserRow row : orEmpty(bloodOxygenMapper.getTopUsers(limit, startDate, endDate))) {
            result.add(new BloodOxygenTopUserView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    intValue(row.getCount())
            ));
        }
        return result;
    }

    @Override
    public List<BloodOxygenDepartmentStatView> getDepartmentStats(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("department-stats", startDate, endDate);
        List<BloodOxygenDepartmentStatView> hit = departmentStatsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<BloodOxygenDepartmentStatView> result = new ArrayList<>();
        for (BloodOxygenDepartmentStatRow row : orEmpty(bloodOxygenMapper.getDepartmentStats(startDate, endDate))) {
            result.add(new BloodOxygenDepartmentStatView(
                    stringValue(row.getDeptName()),
                    intValue(row.getAvgBloodOxygen()),
                    intValue(row.getLowCount()),
                    intValue(row.getHighCount()),
                    intValue(row.getTotalCount())
            ));
        }
        departmentStatsCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<BloodOxygenAgeStatView> getAgeDistribution(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("age-stats", startDate, endDate);
        List<BloodOxygenAgeStatView> hit = ageStatsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<BloodOxygenAgeStatView> result = new ArrayList<>();
        for (BloodOxygenAgeStatRow row : orEmpty(bloodOxygenMapper.getAgeDistribution(startDate, endDate))) {
            result.add(new BloodOxygenAgeStatView(
                    stringValue(row.getAgeRange()),
                    intValue(row.getAvgBloodOxygen())
            ));
        }
        ageStatsCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<BloodOxygenHourlyView> getHourlyStats(String startDate, String endDate) {
        List<BloodOxygenHourlyView> result = new ArrayList<>();
        for (BloodOxygenHourlyRow row : orEmpty(bloodOxygenMapper.getHourlyStats(startDate, endDate))) {
            if (row == null) {
                continue;
            }
            result.add(new BloodOxygenHourlyView(
                    intValue(row.getHour()),
                    doubleValue(row.getAvgBloodOxygen())
            ));
        }
        return result;
    }

    @Override
    public List<BloodOxygenRealtimeView> getRealtime(int limit) {
        List<BloodOxygenRealtimeView> result = new ArrayList<>();
        for (BloodOxygenRealtimeRow row : orEmpty(bloodOxygenMapper.getRealtime(limit))) {
            result.add(new BloodOxygenRealtimeView(
                    stringValue(row.getUserCode()),
                    stringValue(row.getUserName()),
                    stringValue(row.getDeptName()),
                    nullableInt(row.getBloodOxygen()),
                    stringValue(row.getRecordTime())
            ));
        }
        return result;
    }

    private BloodOxygenOverviewView toOverviewView(BloodOxygenOverviewRow row) {
        return new BloodOxygenOverviewView(
                intValue(row == null ? null : row.getAvgBloodOxygen()),
                intValue(row == null ? null : row.getMaxBloodOxygen()),
                intValue(row == null ? null : row.getMinBloodOxygen()),
                intValue(row == null ? null : row.getNormalCount()),
                intValue(row == null ? null : row.getAbnormalCount()),
                intValue(row == null ? null : row.getTotalCount()),
                intValue(row == null ? null : row.getDetectionRate())
        );
    }

    private BloodOxygenTrendView toTrendView(List<BloodOxygenTrendRow> rows) {
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (BloodOxygenTrendRow row : orEmpty(rows)) {
            dates.add(DateParamUtil.shortDate(stringValue(row.getDate())));
            values.add(intValue(row.getAvgBloodOxygen()));
        }
        return new BloodOxygenTrendView(dates, values);
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

    private double doubleValue(Number value) {
        return value == null ? 0D : value.doubleValue();
    }
}
