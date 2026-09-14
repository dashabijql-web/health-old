package com.xzkj.health.observability;

import com.xzkj.health.service.DeviceManagerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class HealthMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong oldBufferSize = new AtomicLong();
    private final AtomicLong newBufferSize = new AtomicLong();

    public HealthMetricsService(MeterRegistry meterRegistry,
                                ObjectProvider<DeviceManagerService> deviceManagerProvider) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("health.buffer.queue.size", oldBufferSize, AtomicLong::get)
                .description("Latest observed Redis buffer queue size")
                .tag("source", "old")
                .register(meterRegistry);
        Gauge.builder("health.buffer.queue.size", newBufferSize, AtomicLong::get)
                .description("Latest observed Redis buffer queue size")
                .tag("source", "new")
                .register(meterRegistry);

        Gauge.builder("health.watch.online.count",
                        () -> readOnlineCount(deviceManagerProvider))
                .description("Current online watch count")
                .register(meterRegistry);
        Gauge.builder("health.watch.online.count",
                        () -> readOnlineCountByProtocol(deviceManagerProvider, DeviceManagerService.ConnectionProtocol.TCP))
                .description("Current online watch count by protocol")
                .tag("protocol", "tcp")
                .register(meterRegistry);
        Gauge.builder("health.watch.online.count",
                        () -> readOnlineCountByProtocol(deviceManagerProvider, DeviceManagerService.ConnectionProtocol.SCTP))
                .description("Current online watch count by protocol")
                .tag("protocol", "sctp")
                .register(meterRegistry);

        registerBaselineMeters();
    }

    private void registerBaselineMeters() {
        Timer.builder("health.ai.call.duration")
                .description("AI provider call duration")
                .tag("mode", "bootstrap")
                .tag("result", "none")
                .register(meterRegistry);
        Counter.builder("health.ai.reject.total")
                .description("Rejected AI requests")
                .tag("stage", "bootstrap")
                .tag("reason", "none")
                .register(meterRegistry);
        Counter.builder("health.ai.sql.auto_repair.total")
                .description("AI SQL auto-repair attempts")
                .tag("result", "none")
                .register(meterRegistry);
        Counter.builder("health.datasource.request.total")
                .description("HTTP request data-source hits")
                .tag("source", "bootstrap")
                .tag("outcome", "none")
                .register(meterRegistry);
        Counter.builder("health.datasource.watch.route.total")
                .description("Watch data-source routing decisions")
                .tag("source", "bootstrap")
                .tag("matched_simulator", "false")
                .register(meterRegistry);
        Counter.builder("health.buffer.push.total")
                .description("Health buffer push operations")
                .tag("source", "bootstrap")
                .tag("mode", "none")
                .register(meterRegistry);
        Counter.builder("health.buffer.flush.total")
                .description("Health buffer flush result count")
                .tag("source", "bootstrap")
                .tag("result", "none")
                .register(meterRegistry);
        DistributionSummary.builder("health.buffer.flush.batch.size")
                .description("Health buffer flush batch size")
                .tag("source", "bootstrap")
                .tag("result", "none")
                .register(meterRegistry);
        Counter.builder("health.buffer.dead_letter.total")
                .description("Dead-lettered health buffer records")
                .tag("source", "bootstrap")
                .register(meterRegistry);
        Counter.builder("health.warning.dedup.total")
                .description("Warning dedup outcomes")
                .tag("indicator", "bootstrap")
                .tag("result", "none")
                .register(meterRegistry);
        Counter.builder("health.warning.generated.total")
                .description("Generated warning records")
                .tag("source", "bootstrap")
                .tag("type", "none")
                .tag("level", "none")
                .tag("result", "none")
                .register(meterRegistry);
        Timer.builder("health.sql.statement.duration")
                .description("MyBatis statement duration")
                .tag("statement", "bootstrap")
                .tag("command", "none")
                .register(meterRegistry);
        Counter.builder("health.sql.slow.total")
                .description("Slow MyBatis statements")
                .tag("statement", "bootstrap")
                .tag("command", "none")
                .register(meterRegistry);
    }

    public void recordAiCall(String mode, String result, long elapsedMs) {
        Timer.builder("health.ai.call.duration")
                .description("AI provider call duration")
                .tag("mode", safeTag(mode))
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .record(Math.max(0L, elapsedMs), TimeUnit.MILLISECONDS);
    }

    public void recordAiReject(String stage, String reason) {
        Counter.builder("health.ai.reject.total")
                .description("Rejected AI requests")
                .tag("stage", safeTag(stage))
                .tag("reason", safeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordAiAutoRepair(String result) {
        Counter.builder("health.ai.sql.auto_repair.total")
                .description("AI SQL auto-repair attempts")
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordRequestSourceHit(String source, String outcome) {
        Counter.builder("health.datasource.request.total")
                .description("HTTP request data-source hits")
                .tag("source", safeTag(source))
                .tag("outcome", safeTag(outcome))
                .register(meterRegistry)
                .increment();
    }

    public void recordWatchSourceRoute(String source, boolean matchedSimulatorPattern) {
        Counter.builder("health.datasource.watch.route.total")
                .description("Watch data-source routing decisions")
                .tag("source", safeTag(source))
                .tag("matched_simulator", String.valueOf(matchedSimulatorPattern))
                .register(meterRegistry)
                .increment();
    }

    public void recordBufferPush(String source, String mode) {
        Counter.builder("health.buffer.push.total")
                .description("Health buffer push operations")
                .tag("source", safeTag(source))
                .tag("mode", safeTag(mode))
                .register(meterRegistry)
                .increment();
    }

    public void updateBufferLength(String source, long size) {
        AtomicLong gauge = "old".equalsIgnoreCase(source) ? oldBufferSize : newBufferSize;
        gauge.set(Math.max(0L, size));
    }

    public void recordBufferFlush(String source, String result, int batchSize) {
        Counter.builder("health.buffer.flush.total")
                .description("Health buffer flush result count")
                .tag("source", safeTag(source))
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("health.buffer.flush.batch.size")
                .description("Health buffer flush batch size")
                .tag("source", safeTag(source))
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .record(Math.max(0, batchSize));
    }

    public void recordBufferDeadLetter(String source, int count) {
        Counter.builder("health.buffer.dead_letter.total")
                .description("Dead-lettered health buffer records")
                .tag("source", safeTag(source))
                .register(meterRegistry)
                .increment(Math.max(0, count));
    }

    public void recordWarningDedup(String indicator, String result) {
        Counter.builder("health.warning.dedup.total")
                .description("Warning dedup outcomes")
                .tag("indicator", safeTag(indicator))
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordWarningGenerated(String source, String warningType, String level, String result) {
        Counter.builder("health.warning.generated.total")
                .description("Generated warning records")
                .tag("source", safeTag(source))
                .tag("type", safeTag(warningType))
                .tag("level", safeTag(level))
                .tag("result", safeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordSqlStatement(String mapperId, String commandType, long elapsedMs, boolean slow) {
        Timer.builder("health.sql.statement.duration")
                .description("MyBatis statement duration")
                .tag("statement", simplifyMapperId(mapperId))
                .tag("command", safeTag(commandType))
                .register(meterRegistry)
                .record(Math.max(0L, elapsedMs), TimeUnit.MILLISECONDS);

        if (slow) {
            Counter.builder("health.sql.slow.total")
                    .description("Slow MyBatis statements")
                    .tag("statement", simplifyMapperId(mapperId))
                    .tag("command", safeTag(commandType))
                    .register(meterRegistry)
                    .increment();
        }
    }

    private String simplifyMapperId(String mapperId) {
        if (mapperId == null || mapperId.isBlank()) {
            return "unknown";
        }
        int index = mapperId.lastIndexOf('.');
        return index >= 0 ? mapperId.substring(index + 1) : mapperId;
    }

    private String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.replace(' ', '-');
    }

    private double readOnlineCount(ObjectProvider<DeviceManagerService> deviceManagerProvider) {
        DeviceManagerService deviceManagerService = deviceManagerProvider.getIfAvailable();
        return deviceManagerService == null ? 0D : deviceManagerService.getOnlineCount();
    }

    private double readOnlineCountByProtocol(ObjectProvider<DeviceManagerService> deviceManagerProvider,
                                             DeviceManagerService.ConnectionProtocol protocol) {
        DeviceManagerService deviceManagerService = deviceManagerProvider.getIfAvailable();
        return deviceManagerService == null ? 0D : deviceManagerService.getOnlineCountByProtocol(protocol);
    }
}
