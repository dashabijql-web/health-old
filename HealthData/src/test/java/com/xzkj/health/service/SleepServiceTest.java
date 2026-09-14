package com.xzkj.health.service;

import com.xzkj.health.dto.sleep.SleepPageDataView;
import com.xzkj.health.dto.sleep.SleepQualityDistributionView;
import com.xzkj.health.dto.sleep.SleepTrendView;
import com.xzkj.health.config.datasource.HealthAsyncQueryExecutor;
import com.xzkj.health.mapper.SleepMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SleepServiceTest {

    @Mock
    private SleepMapper sleepMapper;

    @Mock
    private HealthAsyncQueryExecutor asyncQueryExecutor;

    @InjectMocks
    private SleepService sleepService;

    @Test
    void getSleepTrendCachesTypedView() {
        when(sleepMapper.getSleepTrend(7)).thenReturn(List.of(Map.of(
                "date", "2026-05-07",
                "avgSleepHours", 7.5,
                "avgDeepSleep", 1.5,
                "avgLightSleep", 4.8
        )));

        SleepTrendView first = sleepService.getSleepTrend(7);
        SleepTrendView second = sleepService.getSleepTrend(7);

        assertEquals("05-07", first.dates().get(0));
        assertEquals(7.5, first.avgData().get(0));
        assertSame(first, second);
        verify(sleepMapper, times(1)).getSleepTrend(7);
    }

    @Test
    void getQualityDistributionCachesTypedView() {
        when(sleepMapper.getSleepQualityDistribution()).thenReturn(List.of(
                Map.of("quality", "excellent", "count", 3),
                Map.of("quality", "good", "count", 5),
                Map.of("quality", "fair", "count", 2),
                Map.of("quality", "poor", "count", 1)
        ));

        SleepQualityDistributionView first = sleepService.getQualityDistribution();
        SleepQualityDistributionView second = sleepService.getQualityDistribution();

        assertEquals(3, first.excellent());
        assertEquals(1, first.poor());
        assertSame(first, second);
        verify(sleepMapper, times(1)).getSleepQualityDistribution();
    }

    @Test
    void getPageDataMapsTypedSectionsAndCaches() {
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return CompletableFuture.completedFuture(supplier.get());
        }).when(asyncQueryExecutor).supply(any());

        when(sleepMapper.getLastNightOverviewDirect(any())).thenReturn(Map.of(
                "uploadRate", 88,
                "greenLineRate", 64,
                "avgSleepTime", 7.5,
                "avgScore", 78
        ));
        when(sleepMapper.getSleepDurationDistribution()).thenReturn(Map.of(
                "less4", 1L,
                "range4to6", 2L,
                "range6to8", 5L,
                "more8", 2L,
                "total", 10L
        ));
        when(sleepMapper.getSleepCategoryDistribution()).thenReturn(Map.of(
                "deepSleep", 200L,
                "lightSleep", 500L,
                "dream", 200L,
                "awake", 100L
        ));
        when(sleepMapper.getDeptUploadStats()).thenReturn(List.of(Map.of("deptName", "综采队", "count", 92)));
        when(sleepMapper.getLatestSleepDetails()).thenReturn(List.of(Map.of(
                "userName", "张三",
                "sleepHours", 7.5,
                "score", 88,
                "level", "good",
                "recordTime", "2026-05-07 08:00:00"
        )));

        SleepPageDataView first = sleepService.getSleepPageData();
        SleepPageDataView second = sleepService.getSleepPageData();

        assertEquals(88, first.overview().uploadRate());
        assertEquals(10, first.overview().totalCount());
        assertEquals(4, first.durationLegend().size());
        assertEquals("综采队", first.deptUpload().get(0).deptName());
        assertEquals("7小时30分钟", first.detailList().get(0).sleepHours());
        assertEquals("良", first.detailList().get(0).levelText());
        assertSame(first, second);
        verify(sleepMapper, times(1)).getLatestSleepDetails();
    }
}
