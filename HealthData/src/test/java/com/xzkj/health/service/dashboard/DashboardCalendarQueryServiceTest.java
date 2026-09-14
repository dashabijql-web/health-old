package com.xzkj.health.service.dashboard;

import com.xzkj.health.dto.dashboard.CalendarDayView;
import com.xzkj.health.dto.dashboard.DashboardDayHeartRateRankRow;
import com.xzkj.health.dto.dashboard.DashboardDailyHealthTrendRow;
import com.xzkj.health.dto.dashboard.DashboardWarningCountRow;
import com.xzkj.health.dto.dashboard.DayHeartRateRankView;
import com.xzkj.health.dto.dashboard.HealthTrendView;
import com.xzkj.health.dto.dashboard.WarningDistributionView;
import com.xzkj.health.mapper.DashboardCalendarMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardCalendarQueryServiceTest {

    @Mock
    private DashboardCalendarMapper dashboardCalendarMapper;

    @Test
    void getCalendarDataMergesTrendAndWarningRows() {
        DashboardCalendarQueryService service = new DashboardCalendarQueryService(dashboardCalendarMapper);

        List<DashboardDailyHealthTrendRow> trends = new ArrayList<>();
        DashboardDailyHealthTrendRow day1 = new DashboardDailyHealthTrendRow();
        day1.setDate("2026-05-01");
        day1.setAvgHeartRate(80);
        trends.add(day1);

        List<DashboardWarningCountRow> warnings = new ArrayList<>();
        DashboardWarningCountRow warningDay1 = new DashboardWarningCountRow();
        warningDay1.setStatDate("2026-05-01");
        warningDay1.setCnt(3);
        warnings.add(warningDay1);
        DashboardWarningCountRow warningDay2 = new DashboardWarningCountRow();
        warningDay2.setStatDate("2026-05-02");
        warningDay2.setCnt(1);
        warnings.add(warningDay2);

        when(dashboardCalendarMapper.getDailyHealthTrendDirect(anyString(), eq("2026-05-01"), eq("2026-05-31")))
                .thenReturn(trends);
        when(dashboardCalendarMapper.getWarningCountsByDateDirect(anyString(), eq("2026-05-01"), eq("2026-05-31")))
                .thenReturn(warnings);

        List<CalendarDayView> result = service.getCalendarData(2026, 5);

        assertEquals(2, result.size());
        assertEquals("2026-05-01", result.get(0).date());
        assertEquals(80, result.get(0).avgHeartRate());
        assertEquals(3, result.get(0).warningCount());
        assertEquals("2026-05-02", result.get(1).date());
        assertEquals(1, result.get(1).warningCount());
    }

    @Test
    void getDailyHealthTrendBuildsSeriesWithGaps() {
        DashboardCalendarQueryService service = new DashboardCalendarQueryService(dashboardCalendarMapper);
        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(6).toString();
        String endDate = today.toString();
        String activeDate = today.minusDays(1).toString();

        List<DashboardDailyHealthTrendRow> rows = new ArrayList<>();
        DashboardDailyHealthTrendRow row = new DashboardDailyHealthTrendRow();
        row.setDate(activeDate);
        row.setAvgHeartRate(81);
        row.setAvgBloodOxygen(98);
        row.setAvgSteps(6800);
        rows.add(row);

        when(dashboardCalendarMapper.getDailyHealthTrendDirect(anyString(), eq(startDate), eq(endDate)))
                .thenReturn(rows);

        HealthTrendView result = service.getDailyHealthTrend(7);

        assertEquals(7, result.dates().size());
        assertEquals(7, result.heartRate().size());
        assertEquals(1L, result.heartRate().stream().filter(value -> value != null).count());
        assertEquals(81, result.heartRate().stream().filter(value -> value != null).findFirst().orElseThrow());
        assertEquals(98, result.bloodOxygen().stream().filter(value -> value != null).findFirst().orElseThrow());
        assertEquals(6800, result.steps().stream().filter(value -> value != null).findFirst().orElseThrow());
    }

    @Test
    void getWarningDistributionByDateMapsRows() {
        DashboardCalendarQueryService service = new DashboardCalendarQueryService(dashboardCalendarMapper);
        List<DashboardWarningCountRow> rows = new ArrayList<>();
        DashboardWarningCountRow row = new DashboardWarningCountRow();
        row.setStatDate("2026-05-07");
        row.setCnt(4);
        rows.add(row);

        when(dashboardCalendarMapper.getWarningCountsByDateDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(rows);

        WarningDistributionView result = service.getWarningDistributionByDate("2026-05-01", "2026-05-07");

        assertEquals(List.of("2026-05-07"), result.labels());
        assertEquals(List.of(4), result.counts());
    }

    @Test
    void getDayHeartRateRankMapsRows() {
        DashboardCalendarQueryService service = new DashboardCalendarQueryService(dashboardCalendarMapper);
        List<DashboardDayHeartRateRankRow> rows = new ArrayList<>();
        DashboardDayHeartRateRankRow row = new DashboardDayHeartRateRankRow();
        row.setEmpName("李四");
        row.setDeptName("通风队");
        row.setAvgHeartRate(105);
        rows.add(row);

        when(dashboardCalendarMapper.getDayHeartRateRank("2026-05-07")).thenReturn(rows);

        List<DayHeartRateRankView> result = service.getDayHeartRateRank("2026-05-07");

        assertEquals(1, result.size());
        assertEquals("李四", result.get(0).empName());
        assertEquals("通风队", result.get(0).deptName());
        assertEquals(105, result.get(0).avgHeartRate());
    }
}
