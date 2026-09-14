package com.xzkj.health.service.dashboard;

import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.dashboard.DailyAbnormalStatView;
import com.xzkj.health.dto.dashboard.DashboardDailyPersonRow;
import com.xzkj.health.dto.dashboard.DashboardDepartmentHealthCountRow;
import com.xzkj.health.dto.dashboard.DashboardDepartmentRankingRow;
import com.xzkj.health.dto.dashboard.DashboardHealthComparisonRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardPersonStatRow;
import com.xzkj.health.dto.dashboard.DeptDailyPersonView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.DeptRankingView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.mapper.DashboardDepartmentMapper;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.QueryGate;
import com.xzkj.health.util.TableSourceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DashboardDepartmentQueryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long DEPT_STATS_TTL = 10 * 60 * 1000L;
    private static final long DEPT_PERSON_STATS_TTL = 10 * 60 * 1000L;
    private static final long DEPT_HEALTH_COMP_TTL = 5 * 60 * 1000L;
    private static final long PERSON_COUNTS_TTL = 60 * 1000L;

    private final DashboardDepartmentMapper dashboardDepartmentMapper;
    private final LocalTtlCache<List<DeptHealthCountView>> deptStatsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<DeptPersonStatView>> deptPersonStatsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<DeptHealthComparisonView>> deptHealthComparisonCache = new LocalTtlCache<>();
    private final LocalTtlCache<PersonCountsView> personCountsCache = new LocalTtlCache<>();

    public DashboardDepartmentQueryService(DashboardDepartmentMapper dashboardDepartmentMapper) {
        this.dashboardDepartmentMapper = dashboardDepartmentMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptHealthCountView> getDeptHealthCounts(String startTime, String endTime) {
        return deptStatsCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), DEPT_STATS_TTL, () -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime, monthEnd());
            int days = 30;
            try {
                long diff = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.parse(s), LocalDate.parse(e)) + 1;
                days = (int) diff;
            } catch (Exception ex) {
                log.debug("日期范围解析失败，使用默认30天: {}", ex.getMessage());
            }

            List<DeptHealthCountView> result = new ArrayList<>();
            for (DashboardDepartmentHealthCountRow item : dashboardDepartmentMapper.getDeptWarningWithTrendDirect(warningSourceForJoin(s, e), s, e, days)) {
                result.add(new DeptHealthCountView(
                        stringValue(item.getName()),
                        intValue(item.getCount()),
                        intValue(item.getPrevCount())
                ));
            }
            return result;
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PersonCountsView getPersonCounts(String startTime, String endTime) {
        return personCountsCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), PERSON_COUNTS_TTL, () -> QueryGate.healthRecord(() -> {
            String[] range = range30(startTime, endTime);
            if (hasCompleteDailySummary(range[0], range[1])) {
                DashboardMetricCountRow summary = dashboardDepartmentMapper
                        .getPersonCountsFromDailySummary(range[0], range[1]);
                if (summary != null) return toPersonCountsView(summary);
            }
            LocalDate startDate = LocalDate.parse(range[0]);
            LocalDate endDate = LocalDate.parse(range[1]);
            String hSrc = TableSourceUtil.healthRecordSource(
                    startDate,
                    endDate,
                    "user_code,record_time,heart_rate,blood_oxygen,blood_pressure_high,blood_pressure_low," +
                            "temperature,sleep_minutes,steps,calories,pressure"
            );
            DashboardMetricCountRow data = dashboardDepartmentMapper.getPersonCountsByRangeDirect(hSrc, range[0], range[1]);
            if (data == null) {
                data = new DashboardMetricCountRow();
            }
            return toPersonCountsView(data);
        }));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptPersonStatView> getDeptPersonStats(String startTime, String endTime) {
        return deptPersonStatsCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), DEPT_PERSON_STATS_TTL, () -> {
            String[] range = range30(startTime, endTime);
            if (hasCompleteDailySummary(range[0], range[1])) {
                return toDeptPersonStatViews(dashboardDepartmentMapper
                        .getDeptPersonStatsFromDailySummary(range[0], range[1]));
            }
            LocalDate startDate = LocalDate.parse(range[0]);
            LocalDate endDate = LocalDate.parse(range[1]);
            String hSrc = TableSourceUtil.healthRecordSource(startDate, endDate, "user_code,record_time");
            String wSrc = TableSourceUtil.warningRecordSource(startDate, endDate, "user_code,create_time");
            return toDeptPersonStatViews(
                    dashboardDepartmentMapper.getDeptPersonStatsDirect(hSrc, wSrc, range[0], range[1])
            );
        });
    }

    private boolean hasCompleteDailySummary(String startTime, String endTime) {
        try {
            long expected = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(startTime), LocalDate.parse(endTime)) + 1;
            return dashboardDepartmentMapper.countRefreshedSummaryDays(startTime, endTime) == expected;
        } catch (Exception ex) {
            log.debug("30天汇总不可用，回退月表查询: {}", ex.getMessage());
            return false;
        }
    }

    private PersonCountsView toPersonCountsView(DashboardMetricCountRow data) {
        return new PersonCountsView(
                intValue(data.getHeartRate()),
                intValue(data.getBloodOxygen()),
                intValue(data.getSleep()),
                intValue(data.getSteps()),
                intValue(data.getTemperature()),
                intValue(data.getPressure()),
                intValue(data.getTotalPersons())
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DailyAbnormalStatView> getMetricDailyDetail(String metricType, String startTime, String endTime) {
        String[] range = range30(startTime, endTime);
        LocalDate startDate = LocalDate.parse(range[0]);
        LocalDate endDate = LocalDate.parse(range[1]);
        String hSrc = TableSourceUtil.healthRecordSource(
                startDate,
                endDate,
                "user_code,record_time,heart_rate,blood_oxygen,steps,temperature,pressure"
        );
        return toDailyAbnormalStatViews(
                dashboardDepartmentMapper.getMetricDailyDetail(hSrc, metricType, range[0], range[1])
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DailyAbnormalStatView> getDeptDailyDetail(String deptName, String startTime, String endTime) {
        String[] range = range30(startTime, endTime);
        LocalDate startDate = LocalDate.parse(range[0]);
        LocalDate endDate = LocalDate.parse(range[1]);
        String hSrc = TableSourceUtil.healthRecordSource(startDate, endDate, "user_code,record_time");
        String wSrc = TableSourceUtil.warningRecordSource(startDate, endDate, "user_code,create_time");
        return toDailyAbnormalStatViews(
                dashboardDepartmentMapper.getDeptDailyDetail(hSrc, wSrc, deptName, range[0], range[1])
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptDailyPersonView> getDeptDailyPersons(String startTime, String endTime) {
        String[] range = range30(startTime, endTime);
        return toDeptDailyPersonViews(dashboardDepartmentMapper.getDeptDailyPersons(range[0], range[1]));
    }

    public List<DeptRankingView> getDeptRanking(String startTime, String endTime) {
        String s = resolve(startTime, monthStart());
        String e = resolve(endTime, monthEnd());
        List<DashboardDepartmentRankingRow> rows = dashboardDepartmentMapper.getDeptRanking(s, e);

        List<DeptRankingView> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DashboardDepartmentRankingRow row = rows.get(i);
            result.add(new DeptRankingView(
                    i + 1,
                    stringValue(row.getDepartment()),
                    intValue(row.getMemberCount()),
                    intValue(row.getHealthScore())
            ));
        }
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptHealthComparisonView> getDeptHealthComparison(int days) {
        return deptHealthComparisonCache.getOrLoad(HealthCacheKeys.key(days), DEPT_HEALTH_COMP_TTL, () -> {
            String tblSrc = TableSourceUtil.healthRecordSource(
                    LocalDate.now().minusDays(days),
                    LocalDate.now(),
                    "user_code,heart_rate,blood_oxygen,blood_pressure_high,sleep_minutes,steps,pressure,record_time"
            );
            return toDeptHealthComparisonViews(
                    dashboardDepartmentMapper.getDeptHealthComparisonDirect(tblSrc, days)
            );
        });
    }

    private String monthStart() {
        return LocalDate.now().withDayOfMonth(1).format(DATE_FMT);
    }

    private String monthEnd() {
        LocalDate d = LocalDate.now();
        return d.withDayOfMonth(d.lengthOfMonth()).format(DATE_FMT);
    }

    private String resolve(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private String warningSourceForJoin(String start, String end) {
        return TableSourceUtil.warningRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "id,user_code,create_time,warning_type,indicator_name,indicator_value,warning_level,is_handled"
        );
    }

    private List<DeptPersonStatView> toDeptPersonStatViews(List<DashboardPersonStatRow> rows) {
        List<DeptPersonStatView> result = new ArrayList<>();
        for (DashboardPersonStatRow row : rows) {
            result.add(new DeptPersonStatView(
                    stringValue(row.getDeptName()),
                    intValue(row.getPersonCount()),
                    intValue(row.getAbnormalPersonCount())
            ));
        }
        return result;
    }

    private List<DailyAbnormalStatView> toDailyAbnormalStatViews(List<DashboardPersonStatRow> rows) {
        List<DailyAbnormalStatView> result = new ArrayList<>();
        for (DashboardPersonStatRow row : rows) {
            result.add(new DailyAbnormalStatView(
                    stringValue(row.getDay()),
                    intValue(row.getPersonCount()),
                    intValue(row.getAbnormalPersonCount())
            ));
        }
        return result;
    }

    private List<DeptDailyPersonView> toDeptDailyPersonViews(List<DashboardDailyPersonRow> rows) {
        List<DeptDailyPersonView> result = new ArrayList<>();
        for (DashboardDailyPersonRow row : rows) {
            result.add(new DeptDailyPersonView(
                    stringValue(row.getDeptName()),
                    stringValue(row.getDay()),
                    intValue(row.getPersonCount())
            ));
        }
        return result;
    }

    private List<DeptHealthComparisonView> toDeptHealthComparisonViews(List<DashboardHealthComparisonRow> rows) {
        List<DeptHealthComparisonView> result = new ArrayList<>();
        for (DashboardHealthComparisonRow row : rows) {
            result.add(new DeptHealthComparisonView(
                    stringValue(row.getDeptName()),
                    intValue(row.getMemberCount()),
                    doubleValue(row.getAvgHeartRate()),
                    doubleValue(row.getAvgBloodOxygen()),
                    doubleValue(row.getAvgSystolic()),
                    doubleValue(row.getAvgSleepMinutes()),
                    doubleValue(row.getAvgSteps()),
                    doubleValue(row.getAvgPressure())
            ));
        }
        return result;
    }

    private String[] range30(String startDate, String endDate) {
        return new String[]{
                startDate == null ? LocalDate.now().minusDays(29).toString() : startDate,
                endDate == null ? LocalDate.now().toString() : endDate
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private Double doubleValue(Number value) {
        return value == null ? null : value.doubleValue();
    }
}
