package com.xzkj.health.service.dashboard;

import com.xzkj.health.dto.dashboard.DashboardDepartmentHealthCountRow;
import com.xzkj.health.dto.dashboard.DashboardHealthComparisonRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardPersonStatRow;
import com.xzkj.health.dto.dashboard.DailyAbnormalStatView;
import com.xzkj.health.dto.dashboard.DeptHealthComparisonView;
import com.xzkj.health.dto.dashboard.DeptHealthCountView;
import com.xzkj.health.dto.dashboard.DeptPersonStatView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.mapper.DashboardDepartmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardDepartmentQueryServiceTest {

    @Mock
    private DashboardDepartmentMapper dashboardDepartmentMapper;

    @Test
    void getDeptHealthCountsMapsRowsToTypedView() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        List<DashboardDepartmentHealthCountRow> rows = new ArrayList<>();
        DashboardDepartmentHealthCountRow row = new DashboardDepartmentHealthCountRow();
        row.setName("研发部");
        row.setCount(11);
        row.setPrevCount(7);
        rows.add(row);

        when(dashboardDepartmentMapper.getDeptWarningWithTrendDirect(anyString(), eq("2026-05-01"), eq("2026-05-07"), eq(7)))
                .thenReturn(rows);

        List<DeptHealthCountView> result = service.getDeptHealthCounts("2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("研发部", result.get(0).name());
        assertEquals(11, result.get(0).count());
        assertEquals(7, result.get(0).prevCount());
    }

    @Test
    void getPersonCountsMapsRowsToTypedView() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        DashboardMetricCountRow row = new DashboardMetricCountRow();
        row.setHeartRate(10);
        row.setBloodOxygen(9);
        row.setSleep(8);
        row.setSteps(7);
        row.setTemperature(6);
        row.setPressure(5);
        row.setTotalPersons(12);

        when(dashboardDepartmentMapper.getPersonCountsByRangeDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(row);

        PersonCountsView result = service.getPersonCounts("2026-05-01", "2026-05-07");

        assertEquals(10, result.heartRate());
        assertEquals(9, result.bloodOxygen());
        assertEquals(12, result.totalPersons());
    }

    @Test
    void getPersonCountsReturnsZeroViewWhenMapperReturnsNull() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        when(dashboardDepartmentMapper.getPersonCountsByRangeDirect(anyString(), eq("2026-04-01"), eq("2026-04-02")))
                .thenReturn(null);

        PersonCountsView result = service.getPersonCounts("2026-04-01", "2026-04-02");

        assertEquals(0, result.heartRate());
        assertEquals(0, result.bloodOxygen());
        assertEquals(0, result.totalPersons());
    }

    @Test
    void getDeptPersonStatsMapsRowsToTypedView() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        List<DashboardPersonStatRow> rows = new ArrayList<>();
        DashboardPersonStatRow row = new DashboardPersonStatRow();
        row.setDeptName("研发部");
        row.setPersonCount(18);
        row.setAbnormalPersonCount(3);
        rows.add(row);

        when(dashboardDepartmentMapper.getDeptPersonStatsDirect(anyString(), anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(rows);

        List<DeptPersonStatView> result = service.getDeptPersonStats("2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("研发部", result.get(0).deptName());
        assertEquals(18, result.get(0).personCount());
        assertEquals(3, result.get(0).abnormalPersonCount());
    }

    @Test
    void getMetricDailyDetailMapsRowsToTypedView() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        List<DashboardPersonStatRow> rows = new ArrayList<>();
        DashboardPersonStatRow row = new DashboardPersonStatRow();
        row.setDay("2026-05-07");
        row.setPersonCount(20);
        row.setAbnormalPersonCount(4);
        rows.add(row);

        when(dashboardDepartmentMapper.getMetricDailyDetail(anyString(), eq("heartRate"), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(rows);

        List<DailyAbnormalStatView> result = service.getMetricDailyDetail("heartRate", "2026-05-01", "2026-05-07");

        assertEquals(1, result.size());
        assertEquals("2026-05-07", result.get(0).day());
        assertEquals(20, result.get(0).personCount());
        assertEquals(4, result.get(0).abnormalPersonCount());
    }

    @Test
    void getDeptHealthComparisonMapsRowsToTypedView() {
        DashboardDepartmentQueryService service = new DashboardDepartmentQueryService(dashboardDepartmentMapper);
        List<DashboardHealthComparisonRow> rows = new ArrayList<>();
        DashboardHealthComparisonRow row = new DashboardHealthComparisonRow();
        row.setDeptName("机电队");
        row.setMemberCount(12);
        row.setAvgHeartRate(79.5);
        row.setAvgBloodOxygen(97.2);
        row.setAvgSystolic(121.0);
        row.setAvgSleepMinutes(402.0);
        row.setAvgSteps(7560.0);
        row.setAvgPressure(44.8);
        rows.add(row);

        when(dashboardDepartmentMapper.getDeptHealthComparisonDirect(anyString(), eq(7))).thenReturn(rows);

        List<DeptHealthComparisonView> result = service.getDeptHealthComparison(7);

        assertEquals(1, result.size());
        assertEquals("机电队", result.get(0).deptName());
        assertEquals(12, result.get(0).memberCount());
        assertEquals(79.5, result.get(0).avgHeartRate());
        assertEquals(44.8, result.get(0).avgPressure());
    }
}
