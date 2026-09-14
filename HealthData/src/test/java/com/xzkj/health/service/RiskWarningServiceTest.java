package com.xzkj.health.service;

import com.xzkj.health.dto.riskwarning.RiskWarningDeptStatView;
import com.xzkj.health.dto.riskwarning.RiskWarningOverviewView;
import com.xzkj.health.dto.riskwarning.RiskWarningPageView;
import com.xzkj.health.dto.riskwarning.RiskWarningLocatorRequest;
import com.xzkj.health.dto.riskwarning.RiskWarningTrendView;
import com.xzkj.health.dto.riskwarning.RiskWarningTypeCountView;
import com.xzkj.health.mapper.RiskWarningMapper;
import com.xzkj.health.observability.HealthMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskWarningServiceTest {

    @Mock
    private RiskWarningMapper riskWarningMapper;

    @Mock
    private HealthMetricsService healthMetricsService;

    @InjectMocks
    private RiskWarningService riskWarningService;

    @Test
    void mapsCoreQueriesToTypedViews() {
        when(riskWarningMapper.getWarningOverview("2026-05-01", "2026-05-07"))
                .thenReturn(Map.of(
                        "heartRateCount", 3,
                        "sleepCount", 2,
                        "bloodOxygenCount", 4,
                        "temperatureCount", 5,
                        "pressureCount", 6,
                        "totalWarnings", 20,
                        "handledWarnings", 12,
                        "pendingWarnings", 8,
                        "dangerCount", 7,
                        "warningCount", 13
                ));
        when(riskWarningMapper.getWarningList(null, null, null, null, null, null, null, "2026-05-01", "2026-05-07", 0, 10))
                .thenReturn(List.of(warningRow()));
        when(riskWarningMapper.countWarnings(null, null, null, null, null, null, null, "2026-05-01", "2026-05-07"))
                .thenReturn(1);
        when(riskWarningMapper.getWarningTrendByType(7))
                .thenReturn(List.of(Map.of(
                        "date", "2026-05-07",
                        "heartRate", 1,
                        "bloodOxygen", 2,
                        "sleep", 3,
                        "temperature", 4,
                        "pressure", 5
                )));
        when(riskWarningMapper.getDeptWarningStats("2026-05-01", "2026-05-07"))
                .thenReturn(List.of(Map.of(
                        "deptName", "综采队",
                        "heartRate", 1,
                        "bloodOxygen", 2,
                        "sleep", 3,
                        "temperature", 4,
                        "pressure", 5,
                        "total", 15
                )));
        when(riskWarningMapper.getTypeDistribution())
                .thenReturn(List.of(Map.of("type", "心率异常", "count", 9)));

        RiskWarningOverviewView overview = riskWarningService.getWarningStats("2026-05-01", "2026-05-07");
        RiskWarningPageView page = riskWarningService.getWarningList(null, null, null, null, null, null, null, "2026-05-01", "2026-05-07", 1, 10);
        RiskWarningTrendView trend = riskWarningService.getWarningTrend(7);
        List<RiskWarningDeptStatView> deptStats = riskWarningService.getDeptWarningStats("2026-05-01", "2026-05-07");
        List<RiskWarningTypeCountView> typeDistribution = riskWarningService.getTypeDistribution();

        assertEquals(3, overview.heartRateCount());
        assertEquals(60, overview.handledRate());
        assertEquals(1, page.list().size());
        assertEquals("心率异常", page.list().get(0).warningType());
        assertEquals("05-07", trend.dates().get(0));
        assertEquals(15, deptStats.get(0).total());
        assertEquals(9, typeDistribution.get(0).count());
    }

    @Test
    void defaultWarningListUsesMonthlyTimeWindowInsteadOfAllMonthView() {
        ArgumentCaptor<String> startAt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endAt = ArgumentCaptor.forClass(String.class);
        when(riskWarningMapper.getWarningListByTimeWindow(eq(null), eq(false), startAt.capture(), endAt.capture(), eq(0), eq(1)))
                .thenReturn(List.of(warningRow()));
        when(riskWarningMapper.countWarningsByTimeWindow(eq(null), eq(false), anyString(), anyString()))
                .thenReturn(8);

        RiskWarningPageView page = riskWarningService.getWarningList(
                null, false, null, null, null, null, null, null, null, 1, 1);

        assertEquals(8, page.total());
        assertEquals(1, page.list().size());
        assertTrue(startAt.getValue().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertTrue(endAt.getValue().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertFalse(startAt.getValue().contains("."));
        assertFalse(endAt.getValue().contains("."));
        verify(riskWarningMapper).getWarningListByTimeWindow(eq(null), eq(false), anyString(), anyString(), eq(0), eq(1));
        verify(riskWarningMapper, never()).countWarnings(
                eq(null), eq(false), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void batchHandlingUsesTimestampSafeLocators() {
        Map<String, Object> row = warningRow();
        when(riskWarningMapper.getWarningDetail(1L, "2026-05-07 10:00:00"))
                .thenReturn(row);
        when(riskWarningMapper.handleWarningInTable(
                "warning_record_202605", 1L, "system", "批量处理"))
                .thenReturn(1);

        boolean handled = riskWarningService.handleBatch(
                List.of(new RiskWarningLocatorRequest(1L, "2026-05-07 10:00:00")), "system");

        assertEquals(true, handled);
    }

    private Map<String, Object> warningRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("userName", "张三");
        row.put("userCode", "EMP1001");
        row.put("deptName", "综采队");
        row.put("gender", 1);
        row.put("age", 32);
        row.put("warningType", "心率异常");
        row.put("warningLevel", "高危");
        row.put("warningValue", "120");
        row.put("indicatorName", "heartRate");
        row.put("eventSource", "HEALTH_THRESHOLD");
        row.put("eventCode", "HEART_RATE");
        row.put("handled", false);
        row.put("createTime", "2026-05-07 10:00:00");
        return row;
    }
}
