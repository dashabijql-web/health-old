package com.xzkj.health.service;

import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.trendwarning.TrendWarningDailyAverageRow;
import com.xzkj.health.dto.trendwarning.TrendWarningPredictionView;
import com.xzkj.health.mapper.TrendWarningMapper;
import com.xzkj.health.service.trend.TrendWarningPredictionCalculator;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrendWarningService {

    private static final long PREDICT_TTL = 5 * 60 * 1000L;

    private final TrendWarningMapper trendWarningMapper;
    private final TrendWarningPredictionCalculator predictionCalculator;
    private final AlertConfigService alertConfigService;
    private final LocalTtlCache<TrendWarningPredictionView> predictCache = new LocalTtlCache<>();

    public TrendWarningService(TrendWarningMapper trendWarningMapper,
                               TrendWarningPredictionCalculator predictionCalculator,
                               AlertConfigService alertConfigService) {
        this.trendWarningMapper = trendWarningMapper;
        this.predictionCalculator = predictionCalculator;
        this.alertConfigService = alertConfigService;
    }

    public TrendWarningPredictionView predict() {
        String cacheKey = HealthCacheKeys.key("predict");
        TrendWarningPredictionView hit = predictCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        int days = 14;
        List<TrendWarningDailyAverageRow> rows = trendWarningMapper.getDailyAverages(healthRecordSource(days), days);
        TrendWarningPredictionView result = predictionCalculator.calculate(rows, alertConfigService.getConfigMap(null));
        predictCache.put(cacheKey, result, PREDICT_TTL);
        return result;
    }

    private String healthRecordSource(int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<String> tables = TableNameUtil.healthRecordTables(start, end);
        if (tables.size() == 1) return tables.get(0);

        String cols = "user_code,heart_rate,blood_oxygen,temperature,blood_pressure_high,pressure,record_time";
        return tables.stream()
                .map(t -> "SELECT " + cols + " FROM " + t)
                .collect(Collectors.joining(" UNION ALL ", "(", ")"));
    }
}
