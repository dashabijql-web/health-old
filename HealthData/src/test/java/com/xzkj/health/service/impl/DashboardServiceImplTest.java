package com.xzkj.health.service.impl;

import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardBodyAverageRow;
import com.xzkj.health.dto.dashboard.DashboardBodyIndicatorsView;
import com.xzkj.health.dto.dashboard.DashboardDailyAnomalyRateRow;
import com.xzkj.health.dto.dashboard.DashboardDeviceStatsRow;
import com.xzkj.health.dto.dashboard.DashboardHourCountRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardOverviewView;
import com.xzkj.health.dto.dashboard.DashboardTopUserRow;
import com.xzkj.health.dto.dashboard.DashboardWarningEventRow;
import com.xzkj.health.dto.dashboard.DashboardWarningRateRow;
import com.xzkj.health.dto.dashboard.DeviceActivationView;
import com.xzkj.health.dto.dashboard.DailyAnomalyRateView;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.dto.dashboard.Top5UserView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.dto.dashboard.WarningEventView;
import com.xzkj.health.mapper.DashboardCalendarMapper;
import com.xzkj.health.mapper.DashboardEntryMapper;
import com.xzkj.health.mapper.DashboardOverviewMapper;
import com.xzkj.health.service.dashboard.DashboardCalendarQueryService;
import com.xzkj.health.service.dashboard.DashboardDepartmentQueryService;
import com.xzkj.health.service.dashboard.DashboardEntryQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private DashboardOverviewMapper dashboardOverviewMapper;

    @Mock
    private DashboardCalendarMapper dashboardCalendarMapper;

    @Mock
    private DashboardEntryMapper dashboardEntryMapper;

    @Mock
    private DashboardCalendarQueryService dashboardCalendarQueryService;

    @Mock
    private DashboardEntryQueryService dashboardEntryQueryService;

    @Mock
    private DashboardDepartmentQueryService dashboardDepartmentQueryService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getCurrentMonthCountsCachesNormalizedResult() {
        DashboardMetricCountRow mapperResult = new DashboardMetricCountRow();
        mapperResult.setHeartRate(11);
        mapperResult.setBloodOxygen(22);
        mapperResult.setSleep(33);
        mapperResult.setSteps(44);
        mapperResult.setTemperature(55);
        mapperResult.setPressure(66);

        when(dashboardOverviewMapper.getCountsByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(mapperResult);

        DashboardOverviewView first = dashboardService.getCurrentMonthCounts("2026-05-01", "2026-05-07");
        DashboardOverviewView second = dashboardService.getCurrentMonthCounts("2026-05-01", "2026-05-07");

        assertEquals(11, first.heartRate());
        assertEquals(66, first.pressure());
        assertSame(first, second);
        verify(dashboardOverviewMapper, times(1))
                .getCountsByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07"));
    }

    @Test
    void getCurrentMonthCountsReturnsZeroViewWhenMapperReturnsNull() {
        when(dashboardOverviewMapper.getCountsByRangeDirect(anyString(), eq("2026-04-01"), eq("2026-04-02")))
                .thenReturn(null);

        DashboardOverviewView result = dashboardService.getCurrentMonthCounts("2026-04-01", "2026-04-02");

        assertEquals(0, result.heartRate());
        assertEquals(0, result.bloodOxygen());
        assertEquals(0, result.pressure());
    }

    @Test
    void getCurrentMonthAverageCachesTypedIndicators() {
        DashboardBodyAverageRow mapperResult = new DashboardBodyAverageRow();
        mapperResult.setAvgPressure(70);
        mapperResult.setAvgBloodOxygen(97);
        mapperResult.setAvgHeartRate(76);
        mapperResult.setAvgSteps(9200);
        mapperResult.setAvgBloodPressureHigh(128);
        mapperResult.setAvgBloodPressureLow(82);
        mapperResult.setAvgCalories(420);
        mapperResult.setAvgSleep(6.4);
        mapperResult.setAvgTemperature(36.5);

        when(dashboardOverviewMapper.getAverageByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(mapperResult);

        DashboardBodyIndicatorsView first = dashboardService.getCurrentMonthAverage("2026-05-01", "2026-05-07");
        DashboardBodyIndicatorsView second = dashboardService.getCurrentMonthAverage("2026-05-01", "2026-05-07");

        assertEquals(70, first.avgPressure());
        assertEquals(36.5, first.avgTemperature());
        assertSame(first, second);
        verify(dashboardOverviewMapper, times(1))
                .getAverageByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07"));
    }

    @Test
    void getCurrentMonthAverageReturnsZeroViewWhenMapperReturnsNull() {
        when(dashboardOverviewMapper.getAverageByRangeDirect(anyString(), eq("2026-04-01"), eq("2026-04-02")))
                .thenReturn(null);

        DashboardBodyIndicatorsView result = dashboardService.getCurrentMonthAverage("2026-04-01", "2026-04-02");

        assertEquals(0, result.avgPressure());
        assertEquals(0, result.avgBloodOxygen());
        assertEquals(0.0, result.avgTemperature());
    }

    @Test
    void getDeptTop5MapsRowsToTypedView() {
        List<DashboardTopUserRow> rows = new ArrayList<>();
        DashboardTopUserRow row = new DashboardTopUserRow();
        row.setUserName("张三");
        row.setUserCode("E001");
        row.setCount(9);
        rows.add(row);

        when(dashboardOverviewMapper.getTop5ByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(rows);

        List<Top5UserView> result = dashboardService.getDeptTop5("2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).userName());
        assertEquals("E001", result.get(0).userCode());
        assertEquals(9, result.get(0).count());
    }

    @Test
    void getCalendarDataMergesTrendAndWarningRows() {
        List<CalendarDayView> expected = List.of(
                new CalendarDayView("2026-05-01", 80, null, null, 3),
                new CalendarDayView("2026-05-02", null, null, null, 1)
        );
        when(dashboardCalendarQueryService.getCalendarData(2026, 5)).thenReturn(expected);

        List<CalendarDayView> result = dashboardService.getCalendarData(2026, 5);

        assertEquals(2, result.size());
        assertEquals("2026-05-01", result.get(0).date());
        assertEquals(80, result.get(0).avgHeartRate());
        assertEquals(3, result.get(0).warningCount());
        assertEquals("2026-05-02", result.get(1).date());
        assertEquals(1, result.get(1).warningCount());

        verify(dashboardCalendarQueryService).getCalendarData(2026, 5);
    }

    @Test
    void getDeviceActivationMapsStatsAndWarningRates() {
        DashboardDeviceStatsRow stats = new DashboardDeviceStatsRow();
        stats.setTotal(10);
        stats.setBoundDevices(8);
        stats.setActiveRate(60);
        stats.setUsageRate(50);
        stats.setWarningRate(30);
        stats.setLowBattery(2);

        List<DashboardWarningRateRow> warningRateRows = new ArrayList<>();
        DashboardWarningRateRow warningRate = new DashboardWarningRateRow();
        warningRate.setName("心率预警率");
        warningRate.setRate(12);
        warningRate.setIcon("el-icon-heart");
        warningRateRows.add(warningRate);

        when(dashboardOverviewMapper.getDeviceStatsByRangeDirect(anyString(), anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(stats);
        when(dashboardOverviewMapper.getWarningRatesByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(warningRateRows);

        DeviceActivationView result = dashboardService.getDeviceActivation("2026-05-01", "2026-05-07");

        assertEquals(10, result.stats().total());
        assertEquals(8, result.stats().boundDevices());
        assertEquals(1, result.warningRates().size());
        assertEquals("心率预警率", result.warningRates().get(0).name());
        assertEquals(12, result.warningRates().get(0).rate());
    }

    @Test
    void getDeviceActivationUsesZeroStatsWhenMapperReturnsNull() {
        when(dashboardOverviewMapper.getDeviceStatsByRangeDirect(anyString(), anyString(), eq("2026-04-01"), eq("2026-04-02")))
                .thenReturn(null);
        when(dashboardOverviewMapper.getWarningRatesByRangeDirect(anyString(), eq("2026-04-01"), eq("2026-04-02")))
                .thenReturn(List.of());

        DeviceActivationView result = dashboardService.getDeviceActivation("2026-04-01", "2026-04-02");

        assertEquals(0, result.stats().total());
        assertEquals(0, result.stats().activeRate());
        assertEquals(0, result.warningRates().size());
    }

    @Test
    void getRecentWarningsMapsRowsToTypedView() {
        List<DashboardWarningEventRow> rows = new ArrayList<>();
        DashboardWarningEventRow row = new DashboardWarningEventRow();
        row.setId(1001L);
        row.setWarningType("心率过高");
        row.setRealName("张三");
        row.setEmpCode("E001");
        row.setIndicatorName("心率");
        row.setIndicatorValue("120");
        row.setCreateTime("2026-05-07 09:00:00");
        row.setWarningLevel("高危");
        row.setHandled(0);
        rows.add(row);

        when(dashboardOverviewMapper.getWarningsByRangeDirect(anyString(), eq(5), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(rows);

        List<WarningEventView> result = dashboardService.getRecentWarnings(5, "2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("心率过高", result.get(0).type());
        assertEquals("张三", result.get(0).userName());
        assertEquals(0, result.get(0).handled());
    }

    @Test
    void getDeptPersonStatsMapsRowsToTypedView() {
        when(dashboardDepartmentQueryService.getDeptPersonStats("2026-05-01", "2026-05-07"))
                .thenReturn(List.of(new DeptPersonStatView("研发部", 18, 3)));

        List<DeptPersonStatView> result = dashboardService.getDeptPersonStats("2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("研发部", result.get(0).deptName());
        assertEquals(18, result.get(0).personCount());
        assertEquals(3, result.get(0).abnormalPersonCount());
    }

    @Test
    void getDailyAnomalyRatesMapsSummaryRowsToTypedView() {
        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(6).toString();
        String endDate = today.toString();
        List<DashboardDailyAnomalyRateRow> rows = new ArrayList<>();
        DashboardDailyAnomalyRateRow row = new DashboardDailyAnomalyRateRow();
        row.setDate(endDate);
        row.setHeartRateRate(12.5);
        row.setBloodOxygenRate(3.5);
        row.setTemperatureRate(1.0);
        row.setPressureRate(8.0);
        rows.add(row);

        when(dashboardOverviewMapper.getDailyStatsFromSummary(eq(startDate), eq(endDate))).thenReturn(rows);

        List<DailyAnomalyRateView> result = dashboardService.getDailyAnomalyRates(7);

        assertEquals(1, result.size());
        assertEquals(endDate, result.get(0).date());
        assertEquals(12.5, result.get(0).heartRateRate());
        assertEquals(3.5, result.get(0).bloodOxygenRate());
    }

    @Test
    void getDailyHealthTrendBuildsTypedSeriesWithGaps() {
        HealthTrendView expected = new HealthTrendView(
                List.of("05-01", "05-02"),
                Arrays.asList(81, null),
                Arrays.asList(98, null),
                Arrays.asList(6800, null)
        );
        when(dashboardCalendarQueryService.getDailyHealthTrend(7)).thenReturn(expected);

        HealthTrendView result = dashboardService.getDailyHealthTrend(7);

        assertEquals(2, result.dates().size());
        assertEquals(2, result.heartRate().size());
        assertEquals(1L, result.heartRate().stream().filter(value -> value != null).count());
        assertEquals(81, result.heartRate().stream().filter(value -> value != null).findFirst().orElseThrow());
        assertEquals(98, result.bloodOxygen().stream().filter(value -> value != null).findFirst().orElseThrow());
        assertEquals(6800, result.steps().stream().filter(value -> value != null).findFirst().orElseThrow());
    }

    @Test
    void getDeptHealthCountsMapsRowsToTypedView() {
        when(dashboardDepartmentQueryService.getDeptHealthCounts("2026-05-01", "2026-05-07"))
                .thenReturn(List.of(new DeptHealthCountView("研发部", 11, 7)));

        List<DeptHealthCountView> result = dashboardService.getDeptHealthCounts("2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("研发部", result.get(0).name());
        assertEquals(11, result.get(0).count());
        assertEquals(7, result.get(0).prevCount());
    }

    @Test
    void getWarningDistributionBuildsHourlySeries() {
        List<DashboardHourCountRow> rows = new ArrayList<>();
        DashboardHourCountRow row = new DashboardHourCountRow();
        row.setHourNum(3);
        row.setCount(7);
        rows.add(row);

        when(dashboardOverviewMapper.getWarningCountsByHour(eq("2026-05-07"))).thenReturn(rows);

        WarningDistributionView result = dashboardService.getWarningDistribution("2026-05-07", "2026-05-07", "hour");

        assertEquals(24, result.labels().size());
        assertEquals(24, result.counts().size());
        assertEquals("3", result.labels().get(3));
        assertEquals(7, result.counts().get(3));
    }

    @Test
    void getPreShiftComplianceNormalizesNumbersAndCalculatesRate() {
        when(dashboardEntryQueryService.getPreShiftCompliance())
                .thenReturn(new PreShiftComplianceView(20, 15, 5, 75));

        PreShiftComplianceView result = dashboardService.getPreShiftCompliance();

        assertEquals(20, result.totalToday());
        assertEquals(15, result.qualifiedCount());
        assertEquals(5, result.failedCount());
        assertEquals(75, result.preShiftRate());
        verify(dashboardEntryQueryService).getPreShiftCompliance();
    }

    @Test
    void getDayHeartRateRankMapsTypedRows() {
        when(dashboardCalendarQueryService.getDayHeartRateRank("2026-05-07"))
                .thenReturn(List.of(new DayHeartRateRankView("李四", "通风队", 105)));

        List<DayHeartRateRankView> result = dashboardService.getDayHeartRateRank("2026-05-07");

        assertEquals(1, result.size());
        assertEquals("李四", result.get(0).empName());
        assertEquals("通风队", result.get(0).deptName());
        assertEquals(105, result.get(0).avgHeartRate());
    }

    @Test
    void getMineEntryListMapsTypedRows() {
        when(dashboardEntryQueryService.getMineEntryList(20))
                .thenReturn(List.of(new MineEntryView(
                        "王五",
                        "E005",
                        "综采队",
                        "采煤工",
                        78,
                        97,
                        126,
                        82,
                        365,
                        "2026-05-07 08:30:00",
                        true
                )));

        List<MineEntryView> result = dashboardService.getMineEntryList(20);

        assertEquals(1, result.size());
        assertEquals("王五", result.get(0).empName());
        assertEquals("E005", result.get(0).empCode());
        assertEquals("综采队", result.get(0).deptName());
        assertEquals("采煤工", result.get(0).jobTypeName());
        assertEquals("2026-05-07 08:30:00", result.get(0).recordTime());
        assertEquals(true, result.get(0).qualified());
    }

    @Test
    void getDeptHealthComparisonMapsTypedRows() {
        when(dashboardDepartmentQueryService.getDeptHealthComparison(7))
                .thenReturn(List.of(new DeptHealthComparisonView("机电队", 12, 79.5, 97.2, 121.0, 402.0, 7560.0, 44.8)));

        List<DeptHealthComparisonView> result = dashboardService.getDeptHealthComparison(7);

        assertEquals(1, result.size());
        assertEquals("机电队", result.get(0).deptName());
        assertEquals(12, result.get(0).memberCount());
        assertEquals(79.5, result.get(0).avgHeartRate());
        assertEquals(44.8, result.get(0).avgPressure());
    }
}
