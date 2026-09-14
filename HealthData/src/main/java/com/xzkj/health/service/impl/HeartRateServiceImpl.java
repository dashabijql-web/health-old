package com.xzkj.health.service.impl;

import com.xzkj.health.common.MapValueUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.heartrate.HeartRateAgeStatView;
import com.xzkj.health.dto.heartrate.HeartRateAbnormalRecordPageView;
import com.xzkj.health.dto.heartrate.HeartRateAbnormalRecordView;
import com.xzkj.health.dto.heartrate.HeartRateDailyAnomalyView;
import com.xzkj.health.dto.heartrate.HeartRateDepartmentStatView;
import com.xzkj.health.dto.heartrate.HeartRateDepartmentUserPageView;
import com.xzkj.health.dto.heartrate.HeartRateDepartmentUserView;
import com.xzkj.health.dto.heartrate.HeartRateDistributionItemView;
import com.xzkj.health.dto.heartrate.HeartRateHourlyView;
import com.xzkj.health.dto.heartrate.HeartRateOverviewView;
import com.xzkj.health.dto.heartrate.HeartRatePeriodUserPageView;
import com.xzkj.health.dto.heartrate.HeartRatePeriodUserView;
import com.xzkj.health.dto.heartrate.HeartRateRealtimeView;
import com.xzkj.health.dto.heartrate.HeartRateTopUserView;
import com.xzkj.health.dto.heartrate.HeartRateTrendView;
import com.xzkj.health.mapper.HeartRateMapper;
import com.xzkj.health.service.HeartRateService;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 心率监测服务实现
 * 异常由 GlobalExceptionHandler 统一处理，Service 层无需 try-catch-rethrow
 */
@Service
public class HeartRateServiceImpl implements HeartRateService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    @Autowired
    private HeartRateMapper heartRateMapper;

    private final LocalTtlCache<HeartRateOverviewView> overviewCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<HeartRateAgeStatView>> ageDistributionCache = new LocalTtlCache<>();
    private final LocalTtlCache<HeartRateTrendView> trendCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<HeartRateDepartmentStatView>> departmentStatsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<HeartRateDailyAnomalyView>> dailyAnomalyCache = new LocalTtlCache<>();

    @Override
    public HeartRateOverviewView getHeartRateOverview(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("overview", startDate, endDate);
        HeartRateOverviewView hit = overviewCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        // 同月或跨月均路由到 Direct 版本，避免 v_health_record UNION ALL 13 表全扫
        // 跨月时用 UNION ALL 子查询（仅 2 张分区表），Druid wall 可接受
        String tableSource = heartRateTableSourceByRange(startDate, endDate);
        HeartRateOverviewView result = toOverviewView(
                heartRateMapper.getHeartRateOverviewDirect(tableSource, startDate, endDate)
        );
        overviewCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<HeartRateTopUserView> getTopUsers(int limit, String startDate, String endDate) {
        // 路由到分区表，避免 v_health_record UNION ALL 全扫描
        String tblSrc = heartRateTableSourceForJoin(startDate, endDate);
        return toTopUserViews(heartRateMapper.getTopUsersDirect(tblSrc, limit, startDate, endDate));
    }

    @Override
    public List<HeartRateAgeStatView> getAgeDistribution(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("age-distribution", startDate, endDate);
        List<HeartRateAgeStatView> hit = ageDistributionCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        // 路由到分区表，避免 v_health_record UNION ALL 全扫描
        String tblSrc = heartRateTableSourceForJoin(startDate, endDate);
        List<HeartRateAgeStatView> result = toAgeStatViews(
                heartRateMapper.getAgeDistributionDirect(tblSrc, startDate, endDate)
        );
        ageDistributionCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<HeartRateDistributionItemView> getHeartRateDistribution(String startDate, String endDate) {
        return toDistributionViews(heartRateMapper.getHeartRateDistributionNew(startDate, endDate));
    }

    /** 解析日期范围所跨的分区表表达式（仅取心率趋势所需列） */
    private static String heartRateTableSource(int days) {
        String source = TableSourceUtil.healthRecordSource(
                LocalDate.now().minusDays(days),
                LocalDate.now(),
                "user_code,heart_rate,record_time"
        );
        return source.startsWith("(") ? source + " AS _hr" : source;
    }

    /** 根据显式日期范围计算分区表表达式（带 AS _hr 别名，用于 CTE 内 FROM ${tableSource}） */
    private static String heartRateTableSourceByRange(String startDate, String endDate) {
        String source = TableSourceUtil.healthRecordSource(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                "user_code,heart_rate,blood_oxygen,temperature,steps,calories," +
                        "sleep_minutes,blood_pressure_high,blood_pressure_low,pressure,record_time"
        );
        return source.startsWith("(") ? source + " AS _hr" : source;
    }

    /** 根据显式日期范围计算分区表表达式（无别名，用于 mapper 中 FROM ${tableSource} hr 带独立别名的场景） */
    private static String heartRateTableSourceForJoin(String startDate, String endDate) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                "user_code,heart_rate,blood_oxygen,temperature,steps,calories," +
                        "sleep_minutes,blood_pressure_high,blood_pressure_low,pressure,record_time"
        );
    }

    @Override
    public HeartRateTrendView getHeartRateTrend(int days) {
        String cacheKey = HealthCacheKeys.key("trend", days);
        HeartRateTrendView hit = trendCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        String tblSrc = heartRateTableSource(days);
        HeartRateTrendView result = toTrendView(heartRateMapper.getHeartRateTrendDirect(tblSrc, days));
        trendCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<HeartRateDepartmentStatView> getDepartmentStats(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("department-stats", startDate, endDate);
        List<HeartRateDepartmentStatView> hit = departmentStatsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        // 使用 ForJoin 版本（无内嵌别名），因为 mapper SQL 末尾会追加 "hr" 作为别名
        String tblSrc = heartRateTableSourceForJoin(startDate, endDate);
        List<HeartRateDepartmentStatView> result = toDepartmentStatViews(
                heartRateMapper.getDepartmentStatsDirect(tblSrc, startDate, endDate)
        );
        departmentStatsCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public List<HeartRateHourlyView> getHourlyStats(String startDate, String endDate) {
        return toHourlyViews(MapValueUtil.orEmpty(heartRateMapper.getHourlyStats(startDate, endDate)));
    }

    @Override
    public List<HeartRateRealtimeView> getRealtime(int limit) {
        // 近2h始终在当月分区表内
        String curMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return toRealtimeViews(MapValueUtil.orEmpty(
                heartRateMapper.getRealtimeDirect("health_record_" + curMonth, limit)
        ));
    }

    @Override
    public List<HeartRateDailyAnomalyView> getDailyAnomalyCount(String startDate, String endDate) {
        String cacheKey = HealthCacheKeys.key("daily-anomaly", startDate, endDate);
        List<HeartRateDailyAnomalyView> hit = dailyAnomalyCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        String tblSrc = heartRateTableSourceByRange(startDate, endDate);
        List<HeartRateDailyAnomalyView> result = toDailyAnomalyViews(MapValueUtil.orEmpty(
                heartRateMapper.getDailyAnomalyCountDirect(tblSrc, startDate, endDate)
        ));
        dailyAnomalyCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    @Override
    public HeartRateDepartmentUserPageView getDepartmentAbnormalUsers(
            String deptName, String startDate, String endDate, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        if (deptName == null || deptName.isBlank()) {
            return new HeartRateDepartmentUserPageView(Collections.emptyList(), 0, safePage, safeSize);
        }
        String tableSource = heartRateTableSourceForJoin(startDate, endDate);
        int offset = (safePage - 1) * safeSize;
        List<HeartRateDepartmentUserView> list = toDepartmentUserViews(
                heartRateMapper.getDepartmentAbnormalUsersDirect(
                        tableSource, deptName.trim(), startDate, endDate, offset, safeSize)
        );
        int total = heartRateMapper.countDepartmentAbnormalUsersDirect(
                tableSource, deptName.trim(), startDate, endDate);
        return new HeartRateDepartmentUserPageView(list, total, safePage, safeSize);
    }

    @Override
    public HeartRatePeriodUserPageView getPeriodUsers(
            String mode, String zone, String startDate, String endDate, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        String safeMode = "abnormal".equalsIgnoreCase(mode) ? "abnormal" : "covered";
        int riskCode = riskCode(zone);
        String tableSource = heartRateTableSourceForJoin(startDate, endDate);
        int offset = (safePage - 1) * safeSize;
        List<HeartRatePeriodUserView> list = toPeriodUserViews(heartRateMapper.getPeriodUsersDirect(
                tableSource, safeMode, riskCode, startDate, endDate, offset, safeSize));
        int total = heartRateMapper.countPeriodUsersDirect(
                tableSource, safeMode, riskCode, startDate, endDate);
        return new HeartRatePeriodUserPageView(list, total, safePage, safeSize);
    }

    @Override
    public HeartRateAbnormalRecordPageView getUserAbnormalRecords(
            String userCode, String startDate, String endDate, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        if (userCode == null || userCode.isBlank()) {
            return new HeartRateAbnormalRecordPageView(Collections.emptyList(), 0, safePage, safeSize);
        }
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        String tableSource = heartRateTableSourceByRange(startDate, endDate);
        int offset = (safePage - 1) * safeSize;
        List<HeartRateAbnormalRecordView> list = toAbnormalRecordViews(
                heartRateMapper.getUserAbnormalRecordsDirect(
                        tableSource, userCode.trim(), startDate, endDate, offset, safeSize));
        int total = heartRateMapper.countUserAbnormalRecordsDirect(
                tableSource, userCode.trim(), startDate, endDate);
        return new HeartRateAbnormalRecordPageView(list, total, safePage, safeSize);
    }

    private HeartRateOverviewView toOverviewView(Map<String, Object> row) {
        return new HeartRateOverviewView(
                MapValueUtil.getInt(row, "avgHeartRate"),
                MapValueUtil.getInt(row, "minHeartRate"),
                MapValueUtil.getInt(row, "maxHeartRate"),
                MapValueUtil.getInt(row, "detectionRate"),
                MapValueUtil.getInt(row, "normalCount"),
                MapValueUtil.getInt(row, "abnormalCount"),
                MapValueUtil.getInt(row, "totalCount"),
                MapValueUtil.getInt(row, "coveredUsers"),
                MapValueUtil.getInt(row, "abnormalUsers"),
                MapValueUtil.getInt(row, "lowUsers"),
                MapValueUtil.getInt(row, "normalUsers"),
                MapValueUtil.getInt(row, "elevatedUsers"),
                MapValueUtil.getInt(row, "dangerUsers")
        );
    }

    private List<HeartRateTopUserView> toTopUserViews(List<Map<String, Object>> rows) {
        List<HeartRateTopUserView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateTopUserView(
                    stringValue(row.get("userCode")),
                    stringValue(row.get("userName")),
                    MapValueUtil.getInt(row, "count"),
                    MapValueUtil.getInt(row, "anomalyDays")
            ));
        }
        return result;
    }

    private List<HeartRateAgeStatView> toAgeStatViews(List<Map<String, Object>> rows) {
        List<HeartRateAgeStatView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateAgeStatView(
                    stringValue(row.get("ageRange")),
                    MapValueUtil.getInt(row, "avgHeartRate")
            ));
        }
        return result;
    }

    private List<HeartRateDistributionItemView> toDistributionViews(List<Map<String, Object>> rows) {
        List<HeartRateDistributionItemView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateDistributionItemView(
                    stringValue(row.get("name")),
                    MapValueUtil.getInt(row, "value"),
                    stringValue(row.get("color"))
            ));
        }
        return result;
    }

    private HeartRateTrendView toTrendView(List<Map<String, Object>> rows) {
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            dates.add(com.xzkj.health.common.DateParamUtil.shortDate(stringValue(row.get("date"))));
            values.add(MapValueUtil.getInt(row, "avgHeartRate"));
        }
        return new HeartRateTrendView(dates, values);
    }

    private List<HeartRateDepartmentStatView> toDepartmentStatViews(List<Map<String, Object>> rows) {
        List<HeartRateDepartmentStatView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateDepartmentStatView(
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "avgHeartRate"),
                    MapValueUtil.getInt(row, "lowCount"),
                    MapValueUtil.getInt(row, "highCount"),
                    MapValueUtil.getInt(row, "abnormalCount"),
                    MapValueUtil.getInt(row, "totalCount")
            ));
        }
        return result;
    }

    private List<HeartRateHourlyView> toHourlyViews(List<Map<String, Object>> rows) {
        List<HeartRateHourlyView> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new HeartRateHourlyView(
                    MapValueUtil.getInt(row, "hour"),
                    MapValueUtil.getInt(row, "avgHeartRate")
            ));
        }
        return result;
    }

    private List<HeartRateRealtimeView> toRealtimeViews(List<Map<String, Object>> rows) {
        List<HeartRateRealtimeView> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String userCode = stringValue(row.get("userCode"));
            result.add(new HeartRateRealtimeView(
                    userCode,
                    userCode,
                    stringValue(row.get("userName")),
                    stringValue(row.get("deptName")),
                    stringValue(row.get("gender")),
                    nullableInt(row.get("age")),
                    stringValue(row.get("jobType")),
                    nullableInt(row.get("heartRate")),
                    stringValue(row.get("recordTime"))
            ));
        }
        return result;
    }

    private List<HeartRateDailyAnomalyView> toDailyAnomalyViews(List<Map<String, Object>> rows) {
        List<HeartRateDailyAnomalyView> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new HeartRateDailyAnomalyView(
                    stringValue(row.get("date")),
                    MapValueUtil.getInt(row, "anomalyCount"),
                    MapValueUtil.getInt(row, "coveredUsers"),
                    MapValueUtil.getDouble(row, "anomalyRate"),
                    MapValueUtil.getInt(row, "lowCount"),
                    MapValueUtil.getInt(row, "highCount"),
                    MapValueUtil.getInt(row, "abnormalRecords"),
                    MapValueUtil.getInt(row, "totalRecords")
            ));
        }
        return result;
    }

    private List<HeartRatePeriodUserView> toPeriodUserViews(List<Map<String, Object>> rows) {
        List<HeartRatePeriodUserView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRatePeriodUserView(
                    stringValue(row.get("userCode")),
                    stringValue(row.get("userName")),
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "sampleCount"),
                    MapValueUtil.getInt(row, "abnormalCount"),
                    MapValueUtil.getInt(row, "anomalyDays"),
                    MapValueUtil.getInt(row, "lowCount"),
                    MapValueUtil.getInt(row, "highCount"),
                    MapValueUtil.getInt(row, "minHeartRate"),
                    MapValueUtil.getInt(row, "maxHeartRate"),
                    MapValueUtil.getInt(row, "riskCode"),
                    stringValue(row.get("lastSampleTime")),
                    stringValue(row.get("lastRecordTime"))
            ));
        }
        return result;
    }

    private int riskCode(String zone) {
        if (zone == null) return -1;
        return switch (zone.trim().toLowerCase()) {
            case "normal" -> 0;
            case "low" -> 1;
            case "warning" -> 2;
            case "danger" -> 3;
            default -> -1;
        };
    }

    private List<HeartRateAbnormalRecordView> toAbnormalRecordViews(List<Map<String, Object>> rows) {
        List<HeartRateAbnormalRecordView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateAbnormalRecordView(
                    stringValue(row.get("recordTime")),
                    MapValueUtil.getInt(row, "heartRate"),
                    stringValue(row.get("direction")),
                    stringValue(row.get("level"))
            ));
        }
        return result;
    }

    private List<HeartRateDepartmentUserView> toDepartmentUserViews(List<Map<String, Object>> rows) {
        List<HeartRateDepartmentUserView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new HeartRateDepartmentUserView(
                    stringValue(row.get("userCode")),
                    stringValue(row.get("userName")),
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "abnormalCount"),
                    MapValueUtil.getInt(row, "anomalyDays"),
                    MapValueUtil.getInt(row, "lowCount"),
                    MapValueUtil.getInt(row, "highCount"),
                    MapValueUtil.getInt(row, "minHeartRate"),
                    MapValueUtil.getInt(row, "maxHeartRate"),
                    stringValue(row.get("lastRecordTime"))
            ));
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer nullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
