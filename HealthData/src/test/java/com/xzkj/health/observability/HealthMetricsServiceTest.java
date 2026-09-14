package com.xzkj.health.observability;

import com.xzkj.health.service.DeviceManagerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HealthMetricsServiceTest {

    @Test
    void constructorRegistersBaselineMetricFamilies() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<DeviceManagerService> deviceManagerProvider = mock(ObjectProvider.class);

        new HealthMetricsService(registry, deviceManagerProvider);

        Set<String> names = registry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toSet());

        assertThat(names).contains(
                "health.ai.call.duration",
                "health.ai.reject.total",
                "health.ai.sql.auto_repair.total",
                "health.buffer.queue.size",
                "health.buffer.push.total",
                "health.buffer.flush.total",
                "health.buffer.flush.batch.size",
                "health.buffer.dead_letter.total",
                "health.datasource.request.total",
                "health.datasource.watch.route.total",
                "health.watch.online.count",
                "health.warning.generated.total",
                "health.warning.dedup.total",
                "health.sql.statement.duration",
                "health.sql.slow.total"
        );
    }
}
