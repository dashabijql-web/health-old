package com.xzkj.health.service;

import com.xzkj.health.dto.metric.MetricAbnormalRecordPageView;
import com.xzkj.health.dto.metric.MetricRiskSummaryView;
import com.xzkj.health.dto.metric.MetricRiskUserPageView;
import com.xzkj.health.mapper.MetricPeriodRiskMapper;
import com.xzkj.health.service.metric.MetricRiskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricPeriodRiskServiceTest {

    @Mock
    private MetricPeriodRiskMapper mapper;

    @InjectMocks
    private MetricPeriodRiskService service;

    @Test
    void mapsSummaryForConfiguredMetric() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("coveredUsers", 100);
        row.put("abnormalUsers", 12);
        row.put("abnormalRecords", 27);
        row.put("totalRecords", 800);
        row.put("normalUsers", 88);
        row.put("warningUsers", 9);
        row.put("dangerUsers", 3);
        when(mapper.getSummary(eq("pressure"), anyString(), eq("2026-08-01"), eq("2026-08-14")))
                .thenReturn(row);

        MetricRiskSummaryView result = service.getSummary(
                MetricRiskType.PRESSURE, "2026-08-01", "2026-08-14");

        assertEquals(100, result.coveredUsers());
        assertEquals(12, result.abnormalUsers());
        assertEquals(27, result.abnormalRecords());
        assertEquals(100, result.normalUsers() + result.warningUsers() + result.dangerUsers());
    }

    @Test
    void mapsPagedUsersAndDualValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userCode", "EMP1001");
        row.put("userName", "张三");
        row.put("deptName", "综采队");
        row.put("sampleCount", 30);
        row.put("abnormalCount", 5);
        row.put("anomalyDays", 2);
        row.put("primaryMin", 118);
        row.put("primaryMax", 165);
        row.put("secondaryMin", 76);
        row.put("secondaryMax", 102);
        row.put("riskCode", 3);
        row.put("lastSampleTime", "2026-08-14 09:30:00");
        row.put("lastRecordTime", "2026-08-14 09:00:00");
        when(mapper.getPeriodUsers(eq("bloodPressure"), anyString(), eq("abnormal"), eq(-1),
                eq("2026-08-01"), eq("2026-08-14"), eq(0), eq(12))).thenReturn(List.of(row));
        when(mapper.countPeriodUsers(eq("bloodPressure"), anyString(), eq("abnormal"), eq(-1),
                eq("2026-08-01"), eq("2026-08-14"))).thenReturn(1);

        MetricRiskUserPageView result = service.getPeriodUsers(
                MetricRiskType.BLOOD_PRESSURE, "abnormal", null, "2026-08-01", "2026-08-14", 1, 12);

        assertEquals(1, result.total());
        assertEquals(165, result.list().get(0).primaryMax());
        assertEquals(102, result.list().get(0).secondaryMax());
        assertEquals(3, result.list().get(0).riskCode());
        assertEquals("2026-08-14 09:30:00", result.list().get(0).lastSampleTime());
    }

    @Test
    void mapsWarningZoneToExactRiskCode() {
        when(mapper.getPeriodUsers(eq("pressure"), anyString(), eq("covered"), eq(2),
                eq("2026-08-01"), eq("2026-08-14"), eq(0), eq(12))).thenReturn(List.of());
        when(mapper.countPeriodUsers(eq("pressure"), anyString(), eq("covered"), eq(2),
                eq("2026-08-01"), eq("2026-08-14"))).thenReturn(0);

        MetricRiskUserPageView result = service.getPeriodUsers(
                MetricRiskType.PRESSURE, "covered", "warning", "2026-08-01", "2026-08-14", 1, 12);

        assertEquals(0, result.total());
    }

    @Test
    void mapsUserAbnormalRecords() {
        Map<String, Object> row = Map.of(
                "recordTime", "2026-08-14 09:00:00",
                "primaryValue", 87,
                "direction", "low",
                "level", "danger"
        );
        when(mapper.getUserAbnormalRecords(eq("bloodOxygen"), anyString(), eq("EMP1001"),
                eq("2026-08-01"), eq("2026-08-14"), eq(0), eq(20))).thenReturn(List.of(row));
        when(mapper.countUserAbnormalRecords(eq("bloodOxygen"), anyString(), eq("EMP1001"),
                eq("2026-08-01"), eq("2026-08-14"))).thenReturn(1);

        MetricAbnormalRecordPageView result = service.getUserAbnormalRecords(
                MetricRiskType.BLOOD_OXYGEN, "EMP1001", "2026-08-01", "2026-08-14", 1, 20);

        assertEquals(1, result.total());
        assertEquals(87, result.list().get(0).primaryValue());
        assertEquals("danger", result.list().get(0).level());
    }
}
