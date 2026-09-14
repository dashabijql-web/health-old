package com.xzkj.health.service;

import com.xzkj.health.dto.trendwarning.TrendWarningDailyAverageRow;
import com.xzkj.health.dto.trendwarning.TrendWarningPredictionView;
import com.xzkj.health.mapper.TrendWarningMapper;
import com.xzkj.health.model.entity.AlertConfig;
import com.xzkj.health.service.trend.TrendWarningPredictionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendWarningServiceTest {

    @Mock
    private TrendWarningMapper trendWarningMapper;

    @Mock
    private AlertConfigService alertConfigService;

    private TrendWarningService trendWarningService;

    @BeforeEach
    void setUp() {
        trendWarningService = new TrendWarningService(
                trendWarningMapper,
                new TrendWarningPredictionCalculator(),
                alertConfigService
        );
    }

    @Test
    void predictBuildsTypedRiskListAndCachesResult() {
        List<TrendWarningDailyAverageRow> rows = new ArrayList<>();
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-01", 92, 98, 365, 128, 40));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-02", 95, 98, 365, 130, 45));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-03", 98, 97, 366, 132, 50));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-04", 101, 97, 366, 134, 56));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-05", 103, 97, 366, 136, 61));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-06", 105, 96, 367, 138, 67));
        rows.add(row("EMP1001", "张三", "综采队", "2026-05-07", 108, 96, 367, 139, 72));

        when(trendWarningMapper.getDailyAverages(anyString(), anyInt())).thenReturn(rows);
        when(alertConfigService.getConfigMap(null)).thenReturn(Map.of(
                1, config(60, 100),
                2, config(95, 100),
                3, config(36, 37.3),
                4, config(90, 140),
                5, config(20, 75)
        ));

        TrendWarningPredictionView first = trendWarningService.predict();
        TrendWarningPredictionView second = trendWarningService.predict();

        assertEquals(1, first.summary().total());
        assertEquals(1, first.summary().highRisk());
        assertEquals(1, first.list().size());
        assertEquals("EMP1001", first.list().get(0).empCode());
        assertEquals("张三", first.list().get(0).empName());
        assertEquals("综采队", first.list().get(0).deptName());
        assertEquals(3, first.list().get(0).riskLevel());
        assertTrue(first.list().get(0).riskMetrics().size() >= 1);
        assertTrue(first.list().get(0).riskMetrics().stream().anyMatch(metric -> "心率".equals(metric.metricName())));
        assertSame(first, second);
        verify(trendWarningMapper, times(1)).getDailyAverages(anyString(), anyInt());
    }

    private AlertConfig config(double min, double max) {
        return new AlertConfig()
                .setEnabled(1)
                .setNormalMin(BigDecimal.valueOf(min))
                .setNormalMax(BigDecimal.valueOf(max));
    }

    private TrendWarningDailyAverageRow row(
            String empCode,
            String empName,
            String deptName,
            String recordDate,
            double avgHeartRate,
            double avgBloodOxygen,
            double avgTemperature,
            double avgBpHigh,
            double avgPressure
    ) {
        return new TrendWarningDailyAverageRow(
                empCode,
                empName,
                deptName,
                recordDate,
                avgHeartRate,
                avgBloodOxygen,
                avgTemperature,
                avgBpHigh,
                avgPressure
        );
    }
}
