package com.xzkj.health.config.datasource;

import com.xzkj.health.observability.HealthMetricsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchDataSourceResolver {

    private final HealthDataSourceProperties properties;
    private final HealthMetricsService healthMetricsService;

    private volatile List<Pattern> simulatorPatterns = List.of();

    @PostConstruct
    public void init() {
        List<Pattern> compiled = new ArrayList<>();
        for (String raw : properties.getSimulatorImeiPatterns()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(raw));
            } catch (PatternSyntaxException ex) {
                log.warn("忽略无效的模拟器 IMEI 正则: {}, error={}", raw, ex.getMessage());
            }
        }
        this.simulatorPatterns = List.copyOf(compiled);
        log.info("手表双库路由已初始化: watchDefaultSource={}, simulatorSource={}, simulatorPatternCount={}",
                properties.getWatchDefaultSource().key(),
                properties.getSimulatorSource().key(),
                this.simulatorPatterns.size());
    }

    public HealthDataSourceKey resolveWatchSource(String imei) {
        if (imei != null && !imei.isBlank()) {
            for (Pattern pattern : simulatorPatterns) {
                if (pattern.matcher(imei).matches()) {
                    HealthDataSourceKey source = properties.getSimulatorSource();
                    healthMetricsService.recordWatchSourceRoute(source.key(), true);
                    return source;
                }
            }
        }
        HealthDataSourceKey source = properties.getWatchDefaultSource();
        healthMetricsService.recordWatchSourceRoute(source.key(), false);
        return source;
    }

    public HealthDataSourceKey resolveRequestSource(String raw) {
        return HealthDataSourceKey.from(raw, properties.getRequestDefaultSource());
    }
}
