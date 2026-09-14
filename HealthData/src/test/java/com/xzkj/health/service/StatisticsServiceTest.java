package com.xzkj.health.service;

import com.xzkj.health.dto.statistics.DailyRecordCountRow;
import com.xzkj.health.dto.statistics.DailyRecordCountView;
import com.xzkj.health.dto.statistics.DeptHealthSummaryRow;
import com.xzkj.health.dto.statistics.DeptHealthSummaryView;
import com.xzkj.health.dto.statistics.MonthlySummaryRow;
import com.xzkj.health.dto.statistics.MonthlySummaryView;
import com.xzkj.health.dto.statistics.WarningTypeCountRow;
import com.xzkj.health.dto.statistics.WarningTypeCountView;
import com.xzkj.health.mapper.StatisticsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private StatisticsMapper statisticsMapper;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getDeptHealthSummaryMapsAndCachesTypedRows() {
        List<DeptHealthSummaryRow> rows = new ArrayList<>();
        DeptHealthSummaryRow row = new DeptHealthSummaryRow();
        row.setId(7L);
        row.setDeptName("通风队");
        row.setEmployeeCount(18);
        row.setWarningCount(5);
        rows.add(row);

        when(statisticsMapper.getDeptHealthSummary()).thenReturn(rows);

        List<DeptHealthSummaryView> first = statisticsService.getDeptHealthSummary();
        List<DeptHealthSummaryView> second = statisticsService.getDeptHealthSummary();

        assertEquals(1, first.size());
        assertEquals(7L, first.get(0).id());
        assertEquals("通风队", first.get(0).deptName());
        assertEquals(18, first.get(0).employeeCount());
        assertEquals(5, first.get(0).warningCount());
        assertSame(first, second);
        verify(statisticsMapper, times(1)).getDeptHealthSummary();
    }

    @Test
    void getMonthlySummaryMapsTypedRows() {
        List<MonthlySummaryRow> rows = new ArrayList<>();
        MonthlySummaryRow row = new MonthlySummaryRow();
        row.setEmpCode("EMP1001");
        row.setEmpName("张三");
        row.setDeptId(3L);
        row.setDeptName("综采队");
        row.setRecordCount(42);
        row.setAvgHeartRate(76.5);
        row.setAvgBloodOxygen(97.1);
        row.setAvgTemperature(36.4);
        row.setHealthScore(91);
        rows.add(row);

        when(statisticsMapper.getMonthlySummary(anyString(), anyString(), anyString(), anyString())).thenReturn(rows);

        List<MonthlySummaryView> result = statisticsService.getMonthlySummary("2026-05");

        assertEquals(1, result.size());
        assertEquals("EMP1001", result.get(0).empCode());
        assertEquals("张三", result.get(0).empName());
        assertEquals(3L, result.get(0).deptId());
        assertEquals("综采队", result.get(0).deptName());
        assertEquals(42, result.get(0).recordCount());
        assertEquals(91, result.get(0).healthScore());
    }

    @Test
    void getDailyRecordCountsMapsTypedRows() {
        List<DailyRecordCountRow> rows = new ArrayList<>();
        DailyRecordCountRow row = new DailyRecordCountRow();
        row.setDay(7);
        row.setCount(123);
        rows.add(row);

        when(statisticsMapper.getDailyRecordCounts(anyString(), anyString(), anyString())).thenReturn(rows);

        List<DailyRecordCountView> result = statisticsService.getDailyRecordCounts("2026-05");

        assertEquals(1, result.size());
        assertEquals(7, result.get(0).day());
        assertEquals(123, result.get(0).count());
    }

    @Test
    void getWarningTypeCountsFallsBackAndMapsTypedRows() {
        List<WarningTypeCountRow> rows = new ArrayList<>();
        WarningTypeCountRow row = new WarningTypeCountRow();
        row.setName("心率");
        row.setValue(16);
        rows.add(row);

        when(statisticsMapper.getWarningTypeCountsDirect(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("table missing"));
        when(statisticsMapper.getWarningTypeCounts(anyString(), anyString())).thenReturn(rows);

        List<WarningTypeCountView> result = statisticsService.getWarningTypeCounts("2026-05");

        assertEquals(1, result.size());
        assertEquals("心率", result.get(0).name());
        assertEquals(16, result.get(0).value());
        verify(statisticsMapper).getWarningTypeCounts(anyString(), anyString());
    }
}
