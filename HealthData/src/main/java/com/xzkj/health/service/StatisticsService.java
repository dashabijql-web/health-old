package com.xzkj.health.service;

import com.xzkj.health.common.MapValueUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.statistics.DailyRecordCountRow;
import com.xzkj.health.dto.statistics.DailyRecordCountView;
import com.xzkj.health.dto.statistics.DeptHealthSummaryRow;
import com.xzkj.health.dto.statistics.DeptHealthSummaryView;
import com.xzkj.health.dto.statistics.MonthlySummaryRow;
import com.xzkj.health.dto.statistics.MonthlySummaryView;
import com.xzkj.health.dto.statistics.WarningTypeCountRow;
import com.xzkj.health.dto.statistics.WarningTypeCountView;
import com.xzkj.health.mapper.StatisticsMapper;
import com.xzkj.health.util.LocalTtlCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class StatisticsService {

    private static final long DEPT_SUMMARY_TTL = 5 * 60 * 1000L;
    private static final long MONTHLY_SUMMARY_TTL = 2 * 60 * 1000L;
    private static final long DAILY_COUNTS_TTL = 2 * 60 * 1000L;
    private static final long WARNING_TYPES_TTL = 2 * 60 * 1000L;

    @Autowired
    private StatisticsMapper statisticsMapper;

    private final LocalTtlCache<List<DeptHealthSummaryView>> deptSummaryCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<MonthlySummaryView>> monthlySummaryCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<DailyRecordCountView>> dailyCountsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<WarningTypeCountView>> warningTypesCache = new LocalTtlCache<>();

    public List<DeptHealthSummaryView> getDeptHealthSummary() {
        String cacheKey = HealthCacheKeys.key("all");
        List<DeptHealthSummaryView> hit = deptSummaryCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        List<DeptHealthSummaryView> result = toDeptHealthSummaryViews(statisticsMapper.getDeptHealthSummary());
        deptSummaryCache.put(cacheKey, result, DEPT_SUMMARY_TTL);
        return result;
    }

    public List<MonthlySummaryView> getMonthlySummary(String month) {
        String cacheKey = HealthCacheKeys.key(month);
        List<MonthlySummaryView> hit = monthlySummaryCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        String[] range = monthToRange(month);
        String tableName = "health_record_" + month.replace("-", "");
        String warningTableName = "warning_record_" + month.replace("-", "");
        try {
            List<MonthlySummaryView> result = toMonthlySummaryViews(
                    statisticsMapper.getMonthlySummary(tableName, warningTableName, range[0], range[1])
            );
            monthlySummaryCache.put(cacheKey, result, MONTHLY_SUMMARY_TTL);
            return result;
        } catch (Exception e) {
            log.warn("月度汇总查询失败(表 {} 可能不存在): {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<DailyRecordCountView> getDailyRecordCounts(String month) {
        String cacheKey = HealthCacheKeys.key(month);
        List<DailyRecordCountView> hit = dailyCountsCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        String[] range = monthToRange(month);
        String tableName = "health_record_" + month.replace("-", "");
        try {
            List<DailyRecordCountView> result = toDailyRecordCountViews(
                    statisticsMapper.getDailyRecordCounts(tableName, range[0], range[1])
            );
            dailyCountsCache.put(cacheKey, result, DAILY_COUNTS_TTL);
            return result;
        } catch (Exception e) {
            log.warn("每日记录数查询失败(表 {} 可能不存在): {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<WarningTypeCountView> getWarningTypeCounts(String month) {
        String cacheKey = HealthCacheKeys.key(month);
        List<WarningTypeCountView> hit = warningTypesCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        String[] range = monthToRange(month);
        String warningTableName = "warning_record_" + month.replace("-", "");
        try {
            List<WarningTypeCountView> result = toWarningTypeCountViews(
                    statisticsMapper.getWarningTypeCountsDirect(warningTableName, range[0], range[1])
            );
            warningTypesCache.put(cacheKey, result, WARNING_TYPES_TTL);
            return result;
        } catch (Exception e) {
            log.warn("预警类型统计查询失败(表 {} 可能不存在), fallback 到视图: {}", warningTableName, e.getMessage());
            List<WarningTypeCountView> result = toWarningTypeCountViews(
                    statisticsMapper.getWarningTypeCounts(range[0], range[1])
            );
            warningTypesCache.put(cacheKey, result, WARNING_TYPES_TTL);
            return result;
        }
    }

    /** 将 "YYYY-MM" 转为 [startDate, endDate)，如 ["2026-03-01", "2026-04-01"] */
    private String[] monthToRange(String month) {
        YearMonth ym = YearMonth.parse(month);
        return new String[]{ ym.atDay(1).toString(), ym.plusMonths(1).atDay(1).toString() };
    }

    private List<DeptHealthSummaryView> toDeptHealthSummaryViews(List<DeptHealthSummaryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeptHealthSummaryView> result = new ArrayList<>();
        for (DeptHealthSummaryRow row : rows) {
            result.add(new DeptHealthSummaryView(
                    MapValueUtil.toLong(row.getId()),
                    stringValue(row.getDeptName()),
                    intValue(row.getEmployeeCount()),
                    intValue(row.getWarningCount())
            ));
        }
        return result;
    }

    private List<MonthlySummaryView> toMonthlySummaryViews(List<MonthlySummaryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<MonthlySummaryView> result = new ArrayList<>();
        for (MonthlySummaryRow row : rows) {
            result.add(new MonthlySummaryView(
                    stringValue(row.getEmpCode()),
                    stringValue(row.getEmpName()),
                    MapValueUtil.toLong(row.getDeptId()),
                    stringValue(row.getDeptName()),
                    intValue(row.getRecordCount()),
                    doubleValue(row.getAvgHeartRate()),
                    doubleValue(row.getAvgBloodOxygen()),
                    doubleValue(row.getAvgTemperature()),
                    intValue(row.getHealthScore())
            ));
        }
        return result;
    }

    private List<DailyRecordCountView> toDailyRecordCountViews(List<DailyRecordCountRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<DailyRecordCountView> result = new ArrayList<>();
        for (DailyRecordCountRow row : rows) {
            result.add(new DailyRecordCountView(
                    intValue(row.getDay()),
                    intValue(row.getCount())
            ));
        }
        return result;
    }

    private List<WarningTypeCountView> toWarningTypeCountViews(List<WarningTypeCountRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<WarningTypeCountView> result = new ArrayList<>();
        for (WarningTypeCountRow row : rows) {
            result.add(new WarningTypeCountView(
                    stringValue(row.getName()),
                    intValue(row.getValue())
            ));
        }
        return result;
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private Double doubleValue(Number value) {
        return value == null ? null : value.doubleValue();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
