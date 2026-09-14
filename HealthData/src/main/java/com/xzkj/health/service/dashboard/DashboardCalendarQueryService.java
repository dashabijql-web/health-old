package com.xzkj.health.service.dashboard;

import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardDayBloodOxygenRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayHeartRateRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayStepsRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayWarningRow;
import com.xzkj.health.dto.dashboard.DashboardDailyHealthTrendRow;
import com.xzkj.health.dto.dashboard.DashboardWarningCountRow;
import com.xzkj.health.dto.dashboard.DayBloodOxygenRankView;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.DayStepsRankView;
import com.xzkj.health.dto.dashboard.DayWarningView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.mapper.DashboardCalendarMapper;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardCalendarQueryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long CALENDAR_TTL = 5 * 60 * 1000L;

    private final DashboardCalendarMapper dashboardCalendarMapper;
    private final LocalTtlCache<List<CalendarDayView>> calendarCache = new LocalTtlCache<>();

    public DashboardCalendarQueryService(DashboardCalendarMapper dashboardCalendarMapper) {
        this.dashboardCalendarMapper = dashboardCalendarMapper;
    }

    public HealthTrendView getDailyHealthTrend(int days) {
        String startDate = LocalDate.now().minusDays(days - 1).format(DATE_FMT);
        String endDate = LocalDate.now().format(DATE_FMT);
        List<DashboardDailyHealthTrendRow> rows = dashboardCalendarMapper.getDailyHealthTrendDirect(
                healthSource(startDate, endDate),
                startDate,
                endDate
        );

        Map<String, DashboardDailyHealthTrendRow> byDate = new LinkedHashMap<>();
        for (DashboardDailyHealthTrendRow row : rows) {
            byDate.put(row.getDate(), row);
        }

        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        List<Integer> heartRates = new ArrayList<>();
        List<Integer> bloodOxygens = new ArrayList<>();
        List<Integer> stepsList = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dates.add(day.format(shortFmt));
            DashboardDailyHealthTrendRow row = byDate.get(day.format(DATE_FMT));
            heartRates.add(row == null ? null : nullableInt(row.getAvgHeartRate()));
            bloodOxygens.add(row == null ? null : nullableInt(row.getAvgBloodOxygen()));
            stepsList.add(row == null ? null : nullableInt(row.getAvgSteps()));
        }

        return new HealthTrendView(dates, heartRates, bloodOxygens, stepsList);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<CalendarDayView> getCalendarData(Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();

        String cacheKey = HealthCacheKeys.key(y, m);
        List<CalendarDayView> hit = calendarCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        YearMonth ym = YearMonth.of(y, m);
        String startDate = ym.atDay(1).format(DATE_FMT);
        String endDate = ym.atEndOfMonth().format(DATE_FMT);

        List<DashboardDailyHealthTrendRow> trends = dashboardCalendarMapper.getDailyHealthTrendDirect(
                healthSource(startDate, endDate),
                startDate,
                endDate
        );
        List<DashboardWarningCountRow> warnings = dashboardCalendarMapper.getWarningCountsByDateDirect(
                warningSource(startDate, endDate),
                startDate,
                endDate
        );

        Map<String, CalendarDayView> merged = new LinkedHashMap<>();
        for (DashboardDailyHealthTrendRow trend : trends) {
            String date = trend.getDate();
            merged.put(date, new CalendarDayView(
                    date,
                    nullableInt(trend.getAvgHeartRate()),
                    nullableInt(trend.getAvgBloodOxygen()),
                    nullableInt(trend.getAvgSteps()),
                    0
            ));
        }
        for (DashboardWarningCountRow warning : warnings) {
            String date = warning.getStatDate();
            CalendarDayView current = merged.get(date);
            if (current == null) {
                current = new CalendarDayView(date, null, null, null, 0);
            }
            merged.put(date, new CalendarDayView(
                    current.date(),
                    current.avgHeartRate(),
                    current.avgBloodOxygen(),
                    current.avgSteps(),
                    intValue(warning.getCnt())
            ));
        }

        List<CalendarDayView> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(CalendarDayView::date));
        calendarCache.put(cacheKey, result, CALENDAR_TTL);
        return result;
    }

    public WarningDistributionView getWarningDistributionByDate(String startDate, String endDate) {
        List<DashboardWarningCountRow> rows = dashboardCalendarMapper.getWarningCountsByDateDirect(
                warningSource(startDate, endDate),
                startDate,
                endDate
        );
        List<String> labels = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (DashboardWarningCountRow row : rows) {
            labels.add(row.getStatDate());
            counts.add(intValue(row.getCnt()));
        }
        return new WarningDistributionView(labels, counts);
    }

    public List<DayHeartRateRankView> getDayHeartRateRank(String date) {
        List<DayHeartRateRankView> result = new ArrayList<>();
        for (DashboardDayHeartRateRankRow row : dashboardCalendarMapper.getDayHeartRateRank(date)) {
            result.add(new DayHeartRateRankView(
                    row.getEmpName(),
                    row.getDeptName(),
                    nullableInt(row.getAvgHeartRate())
            ));
        }
        return result;
    }

    public List<DayBloodOxygenRankView> getDayBloodOxygenRank(String date) {
        List<DayBloodOxygenRankView> result = new ArrayList<>();
        for (DashboardDayBloodOxygenRankRow row : dashboardCalendarMapper.getDayBloodOxygenRank(date)) {
            result.add(new DayBloodOxygenRankView(
                    row.getEmpName(),
                    row.getDeptName(),
                    nullableDouble(row.getAvgBloodOxygen())
            ));
        }
        return result;
    }

    public List<DayStepsRankView> getDayStepsRank(String date) {
        List<DayStepsRankView> result = new ArrayList<>();
        for (DashboardDayStepsRankRow row : dashboardCalendarMapper.getDayStepsRank(date)) {
            result.add(new DayStepsRankView(
                    row.getEmpName(),
                    row.getDeptName(),
                    nullableInt(row.getAvgSteps())
            ));
        }
        return result;
    }

    public List<DayWarningView> getDayWarnings(String date) {
        List<DayWarningView> result = new ArrayList<>();
        for (DashboardDayWarningRow row : dashboardCalendarMapper.getDayWarnings(date)) {
            result.add(new DayWarningView(
                    row.getEmpName(),
                    row.getDeptName(),
                    row.getWarningType(),
                    row.getIndicatorName(),
                    row.getWarningValue(),
                    row.getWarningLevel(),
                    row.getCreateTime()
            ));
        }
        return result;
    }

    private String healthSource(String start, String end) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "user_code,record_time,heart_rate,blood_oxygen,blood_pressure_high,blood_pressure_low," +
                        "temperature,sleep_minutes,steps,calories,pressure"
        );
    }

    private String warningSource(String start, String end) {
        return TableSourceUtil.warningRecordSource(
                LocalDate.parse(start),
                LocalDate.parse(end),
                "id,user_code,create_time,warning_type,indicator_name,warning_level,is_handled"
        );
    }

    private Integer nullableInt(Number value) {
        return value == null ? null : value.intValue();
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private Double nullableDouble(Number value) {
        return value == null ? null : value.doubleValue();
    }
}
