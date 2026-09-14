package com.xzkj.health.service.impl;

import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardBodyIndicatorsView;
import com.xzkj.health.dto.dashboard.DashboardBodyAverageRow;
import com.xzkj.health.dto.dashboard.DashboardDailyAnomalyRateRow;
import com.xzkj.health.dto.dashboard.DashboardDeviceStatsRow;
import com.xzkj.health.dto.dashboard.DashboardHourCountRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardOverviewView;
import com.xzkj.health.dto.dashboard.DashboardTopUserRow;
import com.xzkj.health.dto.dashboard.DashboardWarningEventRow;
import com.xzkj.health.dto.dashboard.DashboardWarningRateRow;
import com.xzkj.health.dto.dashboard.DayBloodOxygenRankView;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.DayStepsRankView;
import com.xzkj.health.dto.dashboard.DayWarningView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptRankingView;
import com.xzkj.health.dto.dashboard.DeviceActivationView;
import com.xzkj.health.dto.dashboard.DeviceStatsView;
import com.xzkj.health.dto.dashboard.DailyAnomalyRateView;
import com.xzkj.health.dto.dashboard.DeptDailyPersonView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.DailyAbnormalStatView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.dto.dashboard.Top5UserView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.dto.dashboard.WarningRateView;
import com.xzkj.health.dto.dashboard.WarningEventView;
import com.xzkj.health.mapper.DashboardOverviewMapper;
import com.xzkj.health.service.DashboardService;
import com.xzkj.health.service.dashboard.DashboardCalendarQueryService;
import com.xzkj.health.service.dashboard.DashboardDepartmentQueryService;
import com.xzkj.health.service.dashboard.DashboardEntryQueryService;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.QueryGate;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dashboard服务实现
 *
 * 规则：startTime/endTime 为 null 时，自动填入当月月初/月末。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private DashboardOverviewMapper dashboardOverviewMapper;

    @Autowired
    private DashboardCalendarQueryService dashboardCalendarQueryService;

    @Autowired
    private DashboardEntryQueryService dashboardEntryQueryService;

    @Autowired
    private DashboardDepartmentQueryService dashboardDepartmentQueryService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LocalTtlCache<DashboardOverviewView> currentMonthCountsCache = new LocalTtlCache<>();
    private final LocalTtlCache<DashboardBodyIndicatorsView> currentMonthAverageCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<Top5UserView>> top5Cache = new LocalTtlCache<>();
    private final LocalTtlCache<DeviceActivationView> deviceActivationCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<WarningEventView>> warningEventsCache = new LocalTtlCache<>();
    private final LocalTtlCache<WarningDistributionView> warningCountsCache = new LocalTtlCache<>();
    private final LocalTtlCache<List<DailyAnomalyRateView>> dailyTrendCache = new LocalTtlCache<>();

    private static final long BODY_INDICATORS_TTL = 10 * 60 * 1000L;
    private static final long OVERVIEW_TTL = 10 * 60 * 1000L;
    private static final long DAILY_TREND_TTL = 5 * 60 * 1000L;
    private static final long WARNING_COUNTS_TTL = 5 * 60 * 1000L;
    private static final long WARNING_EVENTS_TTL = 30 * 1000L;
    private static final long TOP5_TTL = 2 * 60 * 1000L;
    private static final long DEVICE_ACTIVATION_TTL = 2 * 60 * 1000L;

    /** 当月月初，格式 yyyy-MM-dd */
    private String monthStart() {
        return LocalDate.now().withDayOfMonth(1).format(DATE_FMT);
    }

    /** 当月月末，格式 yyyy-MM-dd */
    private String monthEnd() {
        LocalDate d = LocalDate.now();
        return d.withDayOfMonth(d.lengthOfMonth()).format(DATE_FMT);
    }

    private String resolve(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    /** 根据日期范围构建健康记录分区表源（单月直接用表名，跨月用 UNION ALL 子查询） */
    private String healthSource(String start, String end) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "user_code,record_time,heart_rate,blood_oxygen,blood_pressure_high,blood_pressure_low," +
                        "temperature,sleep_minutes,steps,calories,pressure"
        );
    }

    /**
     * 用于 JOIN 语法的健康记录分区表源（不含内嵌别名，mapper 中会用 hr 做别名）
     * 单月直接返回表名，跨月返回仅含 user_code+record_time 的 UNION ALL 子查询（无 AS xxx）
     */
    private String healthSourceForJoin(String start, String end) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "user_code,record_time"
        );
    }

    /**
     * 用于 JOIN 语法的预警记录分区表源（不含内嵌别名，mapper 中会用 wr/w 做别名）
     * 单月直接返回表名，跨月返回 UNION ALL 子查询（无 AS xxx，包含 id 用于 COUNT DISTINCT）
     */
    private String warningSourceForJoin(String start, String end) {
        return TableSourceUtil.warningRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "id,user_code,create_time,warning_type,indicator_name,indicator_value,warning_level,is_handled"
        );
    }

    /** 根据日期范围构建预警记录分区表源（单月直接用表名，跨月用 UNION ALL 子查询） */
    private String warningSource(String start, String end) {
        return TableSourceUtil.warningRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "id,user_code,create_time,warning_type,indicator_name,warning_level,is_handled"
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public DashboardOverviewView getCurrentMonthCounts(String startTime, String endTime) {
        return currentMonthCountsCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), OVERVIEW_TTL, () -> QueryGate.healthRecord(() -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime,   monthEnd());
            DashboardMetricCountRow data = dashboardOverviewMapper.getCountsByRangeDirect(healthSource(s, e), s, e);
            if (data == null) {
                data = new DashboardMetricCountRow();
            }
            return new DashboardOverviewView(
                    intValue(data.getHeartRate()),
                    intValue(data.getBloodOxygen()),
                    intValue(data.getSleep()),
                    intValue(data.getSteps()),
                    intValue(data.getTemperature()),
                    intValue(data.getPressure())
            );
        }));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public DashboardBodyIndicatorsView getCurrentMonthAverage(String startTime, String endTime) {
        return currentMonthAverageCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), BODY_INDICATORS_TTL, () -> QueryGate.healthRecord(() -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime,   monthEnd());
            DashboardBodyAverageRow data = dashboardOverviewMapper.getAverageByRangeDirect(healthSource(s, e), s, e);
            if (data == null) {
                data = new DashboardBodyAverageRow();
            }
            return new DashboardBodyIndicatorsView(
                    intValue(data.getAvgPressure()),
                    intValue(data.getAvgBloodOxygen()),
                    intValue(data.getAvgHeartRate()),
                    intValue(data.getAvgSteps()),
                    intValue(data.getAvgBloodPressureHigh()),
                    intValue(data.getAvgBloodPressureLow()),
                    intValue(data.getAvgCalories()),
                    round(doubleValue(data.getAvgSleep()), 1),
                    round(doubleValue(data.getAvgTemperature()), 1)
            );
        }));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<Top5UserView> getDeptTop5(String startTime, String endTime) {
        return top5Cache.getOrLoad(HealthCacheKeys.key(startTime, endTime), TOP5_TTL, () -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime,   monthEnd());
            List<DashboardTopUserRow> list = dashboardOverviewMapper.getTop5ByRangeDirect(warningSourceForJoin(s, e), s, e);
            List<Top5UserView> result = new ArrayList<>();
            for (DashboardTopUserRow item : list) {
                result.add(new Top5UserView(
                        stringValue(item.getUserName()),
                        stringValue(item.getUserCode()),
                        intValue(item.getCount())
                ));
            }
            return result;
        });
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public DeviceActivationView getDeviceActivation(String startTime, String endTime) {
        return deviceActivationCache.getOrLoad(HealthCacheKeys.key(startTime, endTime), DEVICE_ACTIVATION_TTL, () -> QueryGate.healthRecord(() -> {
            DeviceStatsView stats = getDeviceStatsView(startTime, endTime);
            List<WarningRateView> warningRates = getWarningRateViews(startTime, endTime);
            return new DeviceActivationView(stats, warningRates);
        }));
    }

    private DeviceStatsView getDeviceStatsView(String startTime, String endTime) {
        String s = resolve(startTime, monthStart());
        String e = resolve(endTime,   monthEnd());
        if (hasCompleteDailySummary(s, e)) {
            return toDeviceStatsView(dashboardOverviewMapper.getDeviceStatsFromDailySummary(s, e));
        }
        String hSrc = healthSourceForJoin(s, e);
        String wSrc = warningSourceForJoin(s, e);
        DashboardDeviceStatsRow data = dashboardOverviewMapper.getDeviceStatsByRangeDirect(hSrc, wSrc, s, e);
        return toDeviceStatsView(data);
    }

    private boolean hasCompleteDailySummary(String startTime, String endTime) {
        try {
            long expected = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(startTime), LocalDate.parse(endTime)) + 1;
            return dashboardOverviewMapper.countRefreshedSummaryDays(startTime, endTime) == expected;
        } catch (Exception ex) {
            return false;
        }
    }

    private List<WarningRateView> getWarningRateViews(String startTime, String endTime) {
        String s = resolve(startTime, monthStart());
        String e = resolve(endTime,   monthEnd());
        // 优化：路由到分区表，避免 v_warning_record UNION ALL 全扫描
        List<DashboardWarningRateRow> rates = dashboardOverviewMapper.getWarningRatesByRangeDirect(warningSource(s, e), s, e);
        List<WarningRateView> result = new ArrayList<>();
        for (DashboardWarningRateRow rate : rates) {
            result.add(new WarningRateView(
                    stringValue(rate.getName()),
                    intValue(rate.getRate()),
                    stringValue(rate.getIcon())
            ));
        }
        return result;
    }

    @Override
    public List<WarningEventView> getRecentWarnings(int limit, String startTime, String endTime) {
        long ttl = limit >= 100 ? WARNING_EVENTS_TTL : Math.min(WARNING_EVENTS_TTL, 10 * 1000L);
        return warningEventsCache.getOrLoad(HealthCacheKeys.key(limit, startTime, endTime), ttl, () -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime,   monthEnd());
            List<DashboardWarningEventRow> list = dashboardOverviewMapper.getWarningsByRangeDirect(warningSourceForJoin(s, e), limit, s, e);
            List<WarningEventView> result = new ArrayList<>();
            for (DashboardWarningEventRow item : list) {
                result.add(new WarningEventView(
                        item.getId(),
                        stringValue(item.getWarningType()),
                        stringValue(item.getRealName()),
                        stringValue(item.getEmpCode()),
                        stringValue(item.getIndicatorName()),
                        stringValue(item.getIndicatorValue()),
                        stringValue(item.getCreateTime()),
                        stringValue(item.getWarningLevel()),
                        intValue(item.getHandled())
                ));
            }
            return result;
        });
    }

    @Override
    public WarningDistributionView getWarningDistribution(String startTime, String endTime, String groupBy) {
        return warningCountsCache.getOrLoad(HealthCacheKeys.key(startTime, endTime, groupBy), WARNING_COUNTS_TTL, () -> {
            String s = resolve(startTime, monthStart());
            String e = resolve(endTime,   monthEnd());
            if ("hour".equals(groupBy)) {
                List<DashboardHourCountRow> rows = dashboardOverviewMapper.getWarningCountsByHour(s);
                int[] hourCounts = new int[24];
                for (DashboardHourCountRow row : rows) {
                    int h = intValue(row.getHourNum());
                    if (h >= 0 && h < 24) hourCounts[h] = intValue(row.getCount());
                }
                List<String> labels = java.util.stream.IntStream.range(0, 24).mapToObj(String::valueOf).collect(java.util.stream.Collectors.toList());
                List<Integer> counts = java.util.Arrays.stream(hourCounts).boxed().collect(java.util.stream.Collectors.toList());
                return new WarningDistributionView(labels, counts);
            }
            return dashboardCalendarQueryService.getWarningDistributionByDate(s, e);
        });
    }

    @Override
    public List<DailyAnomalyRateView> getDailyAnomalyRates(int days) {
        return dailyTrendCache.getOrLoad(HealthCacheKeys.key(days), DAILY_TREND_TTL, () -> {
            LocalDate start = LocalDate.now().minusDays(days - 1);
            LocalDate end   = LocalDate.now();
            String startDate = start.format(DATE_FMT);
            String endDate   = end.format(DATE_FMT);
            return toDailyAnomalyRateViews(
                    dashboardOverviewMapper.getDailyStatsFromSummary(startDate, endDate)
            );
        });
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptHealthCountView> getDeptHealthCounts(String startTime, String endTime) {
        return dashboardDepartmentQueryService.getDeptHealthCounts(startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PersonCountsView getPersonCounts(String startTime, String endTime) {
        return dashboardDepartmentQueryService.getPersonCounts(startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptPersonStatView> getDeptPersonStats(String startTime, String endTime) {
        return dashboardDepartmentQueryService.getDeptPersonStats(startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DailyAbnormalStatView> getMetricDailyDetail(String metricType, String startTime, String endTime) {
        return dashboardDepartmentQueryService.getMetricDailyDetail(metricType, startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DailyAbnormalStatView> getDeptDailyDetail(String deptName, String startTime, String endTime) {
        return dashboardDepartmentQueryService.getDeptDailyDetail(deptName, startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptDailyPersonView> getDeptDailyPersons(String startTime, String endTime) {
        return dashboardDepartmentQueryService.getDeptDailyPersons(startTime, endTime);
    }

    @Override
    public HealthTrendView getDailyHealthTrend(int days) {
        return dashboardCalendarQueryService.getDailyHealthTrend(days);
    }

    @Override
    public List<DeptRankingView> getDeptRanking(String startTime, String endTime) {
        return dashboardDepartmentQueryService.getDeptRanking(startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<CalendarDayView> getCalendarData(Integer year, Integer month) {
        return dashboardCalendarQueryService.getCalendarData(year, month);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PreShiftComplianceView getPreShiftCompliance() {
        return dashboardEntryQueryService.getPreShiftCompliance();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<MineEntryView> getMineEntryList(int size) {
        return dashboardEntryQueryService.getMineEntryList(size);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<DeptHealthComparisonView> getDeptHealthComparison(int days) {
        return dashboardDepartmentQueryService.getDeptHealthComparison(days);
    }

    @Override
    public List<DayHeartRateRankView> getDayHeartRateRank(String date) {
        return dashboardCalendarQueryService.getDayHeartRateRank(date);
    }

    @Override
    public List<DayBloodOxygenRankView> getDayBloodOxygenRank(String date) {
        return dashboardCalendarQueryService.getDayBloodOxygenRank(date);
    }

    @Override
    public List<DayStepsRankView> getDayStepsRank(String date) {
        return dashboardCalendarQueryService.getDayStepsRank(date);
    }

    @Override
    public List<DayWarningView> getDayWarnings(String date) {
        return dashboardCalendarQueryService.getDayWarnings(date);
    }

    // ─── 工具方法 ───────────────────────────────────────────────────

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private DeviceStatsView toDeviceStatsView(DashboardDeviceStatsRow data) {
        if (data == null) {
            data = new DashboardDeviceStatsRow();
        }
        return new DeviceStatsView(
                intValue(data.getTotal()),
                intValue(data.getBoundDevices()),
                intValue(data.getActiveRate()),
                intValue(data.getUsageRate()),
                intValue(data.getWarningRate()),
                intValue(data.getLowBattery())
        );
    }

    private List<DailyAnomalyRateView> toDailyAnomalyRateViews(List<DashboardDailyAnomalyRateRow> rows) {
        List<DailyAnomalyRateView> result = new ArrayList<>();
        for (DashboardDailyAnomalyRateRow row : rows) {
            result.add(new DailyAnomalyRateView(
                    stringValue(row.getDate()),
                    doubleValue(row.getHeartRateRate()),
                    doubleValue(row.getBloodOxygenRate()),
                    doubleValue(row.getTemperatureRate()),
                    doubleValue(row.getPressureRate())
            ));
        }
        return result;
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private double doubleValue(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
