package com.xzkj.health.config.datasource;

import com.xzkj.health.observability.HealthMetricsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WatchDataSourceResolverTest {

    @Test
    void routesSimulatorImeiToSimulatorSource() {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setWatchDefaultSource(HealthDataSourceKey.NEW);
        properties.setSimulatorSource(HealthDataSourceKey.OLD);
        properties.setSimulatorImeiPatterns(List.of("^3594567800\\d{5}$"));

        HealthMetricsService healthMetricsService = mock(HealthMetricsService.class);
        WatchDataSourceResolver resolver = new WatchDataSourceResolver(properties, healthMetricsService);
        resolver.init();

        assertEquals(HealthDataSourceKey.OLD, resolver.resolveWatchSource("359456780012345"));
        assertEquals(HealthDataSourceKey.NEW, resolver.resolveWatchSource("860000000000001"));
        verify(healthMetricsService).recordWatchSourceRoute("old", true);
        verify(healthMetricsService).recordWatchSourceRoute("new", false);
    }

    @Test
    void ignoresInvalidSimulatorRegexAndKeepsValidRules() {
        HealthDataSourceProperties properties = new HealthDataSourceProperties();
        properties.setWatchDefaultSource(HealthDataSourceKey.NEW);
        properties.setSimulatorSource(HealthDataSourceKey.OLD);
        properties.setSimulatorImeiPatterns(List.of("[invalid", "^3594567800\\d{5}$"));

        HealthMetricsService healthMetricsService = mock(HealthMetricsService.class);
        WatchDataSourceResolver resolver = new WatchDataSourceResolver(properties, healthMetricsService);
        resolver.init();

        assertEquals(HealthDataSourceKey.OLD, resolver.resolveWatchSource("359456780012345"));
        assertEquals(HealthDataSourceKey.NEW, resolver.resolveWatchSource("861111111111111"));
        verify(healthMetricsService).recordWatchSourceRoute("old", true);
        verify(healthMetricsService).recordWatchSourceRoute("new", false);
    }
}
