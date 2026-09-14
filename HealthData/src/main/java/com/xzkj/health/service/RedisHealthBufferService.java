package com.xzkj.health.service;

import com.alibaba.fastjson2.JSON;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.HealthDataSourceKey;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.observability.HealthMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 健康数据 Redis 写缓冲服务
 *
 * 解决问题：1000个手表每隔几秒上报一次数据，直接逐条写库会产生大量小事务，
 * 高并发时数据库压力大。
 *
 * 解决方案：
 *   手表数据 → push() 写入 Redis List（内存操作，微秒级）
 *   定时任务  → 每5秒 flush() 一次，批量写入数据库（一次事务插入多条）
 *
 * Redis 数据结构：
 *   Key:   health:buffer
 *   Type:  List
 *   Value: HealthRecord 的 JSON 字符串，每条一个元素
 */
@Slf4j
@Service
public class RedisHealthBufferService {

    private static final String BUFFER_KEY_PREFIX = "health:buffer:";
    private static final String DEAD_LETTER_KEY_PREFIX = "health:buffer:dead:";
    private static final String RETRY_KEY_PREFIX = "health:buffer:retry:";
    private static final String LATEST_KEY_PREFIX = "health:latest:";
    private static final String ONLINE_KEY_PREFIX = "health:online:";
    private static final int MAX_BATCH_SIZE = 2000;
    private static final Duration LATEST_RETENTION = Duration.ofHours(48);
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HealthRecordService healthRecordService;

    @Autowired
    private HealthMetricsService healthMetricsService;

    /**
     * 将健康记录推入 Redis 缓冲队列
     * 由 DataProcessService 在 Netty 处理线程中调用，替代直接写库
     */
    public void push(HealthRecord record) {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        HealthDataSourceKey source = current == null ? HealthDataSourceKey.NEW : current;
        String bufferKey = bufferKey(source);
        try {
            Long newSize = redisTemplate.opsForList().rightPush(bufferKey, JSON.toJSONString(record));
            rememberLatest(source, record);
            healthMetricsService.recordBufferPush(source.key(), "redis");
            if (newSize != null) {
                healthMetricsService.updateBufferLength(source.key(), newSize);
            }
        } catch (Exception e) {
            // Redis 写入失败时降级直接写库，保证数据不丢失
            log.warn("Redis缓冲写入失败，降级直接写库: {}", e.getMessage());
            healthMetricsService.recordBufferPush(source.key(), "direct-write-fallback");
            healthRecordService.save(record);
        }
    }

    /**
     * 定时将缓冲队列中的数据批量写入数据库
     * 每5秒执行一次，批量插入性能远优于逐条插入
     */
    @Scheduled(fixedDelay = 5000)
    public void flush() {
        for (HealthDataSourceKey source : HealthDataSourceKey.values()) {
            flushOneSource(source);
        }
    }

    private void flushOneSource(HealthDataSourceKey source) {
        String bufferKey = bufferKey(source);
        Long size;
        try {
            size = redisTemplate.opsForList().size(bufferKey);
        } catch (Exception e) {
            log.error("健康数据读取buffer长度失败: source={}, key={}", source.key(), bufferKey, e);
            return;
        }
        healthMetricsService.updateBufferLength(source.key(), size == null ? 0L : size);
        if (size == null || size == 0) {
            return;
        }

        int batchSize = (int) Math.min(size, MAX_BATCH_SIZE);
        List<String> items;
        try {
            items = redisTemplate.opsForList().range(bufferKey, 0, batchSize - 1);
        } catch (Exception e) {
            log.error("健康数据读取buffer失败: source={}, key={}, batchSize={}", source.key(), bufferKey, batchSize, e);
            return;
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        log.info("健康数据读取buffer成功: source={}, key={}, bufferSize={}, batchSize={}",
                source.key(), bufferKey, size, items.size());

        ParsedBatch batch = parseBatch(source, items);
        isolateBadRecords(source, bufferKey, batch.badEntries());
        if (!batch.badEntries().isEmpty()) {
            healthMetricsService.recordBufferDeadLetter(source.key(), batch.badEntries().size());
            log.warn("健康数据反序列化失败: source={}, badCount={}, deadLetterKey={}",
                    source.key(), batch.badEntries().size(), deadLetterKey(source));
        }
        healthMetricsService.updateBufferLength(source.key(), Math.max(0L, size - batch.badEntries().size()));
        if (batch.records().isEmpty()) {
            return;
        }

        try {
            HealthDataSourceContext.runWith(source, () -> healthRecordService.batchInsert(batch.records()));
            log.info("健康数据写库成功: source={}, count={}", source.key(), batch.records().size());
            redisTemplate.opsForList().trim(bufferKey, batch.records().size(), -1);
            clearRetryCount(source);
            healthMetricsService.recordBufferFlush(source.key(), "success", batch.records().size());
            healthMetricsService.updateBufferLength(source.key(), Math.max(0L, size - items.size()));
            log.info("健康数据trim成功: source={}, trimmedCount={}", source.key(), batch.records().size());
        } catch (Exception e) {
            long retryCount = incrementRetryCount(source);
            healthMetricsService.recordBufferFlush(source.key(), "failure", batch.records().size());
            log.error("健康数据写库失败: source={}, count={}, retryCount={}",
                    source.key(), batch.records().size(), retryCount, e);
        }
    }

    public List<HealthRecord> latestRecords() {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        HealthDataSourceKey source = current == null ? HealthDataSourceKey.NEW : current;
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(latestKey(source));
            if (entries == null || entries.isEmpty()) return List.of();
            List<HealthRecord> records = new ArrayList<>(entries.size());
            for (Object value : entries.values()) {
                if (!(value instanceof String json) || json.isBlank()) continue;
                try {
                    HealthRecord record = JSON.parseObject(json, HealthRecord.class);
                    if (record != null) records.add(record);
                } catch (Exception ignored) {
                    // skip malformed snapshot entries
                }
            }
            return records;
        } catch (Exception ex) {
            log.warn("读取实时最新快照失败: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 只读取指定实时窗口内有上报的人员，避免每次扫描完整 latest Hash。
     */
    public List<HealthRecord> latestRecords(int windowMinutes) {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        HealthDataSourceKey source = current == null ? HealthDataSourceKey.NEW : current;
        long cutoff = System.currentTimeMillis() - Math.max(1, windowMinutes) * 60_000L;
        try {
            Set<String> userCodes = redisTemplate.opsForZSet()
                    .rangeByScore(onlineKey(source), cutoff, Double.POSITIVE_INFINITY);
            if (userCodes == null || userCodes.isEmpty()) {
                // 兼容升级前只有 latest Hash 的缓存；其中存在数据时由调用方自行按窗口过滤。
                return latestRecords();
            }
            List<Object> hashKeys = new ArrayList<>(userCodes);
            List<Object> values = redisTemplate.opsForHash().multiGet(latestKey(source), hashKeys);
            if (values == null || values.isEmpty()) return List.of();
            List<HealthRecord> records = new ArrayList<>(values.size());
            for (Object value : values) {
                if (!(value instanceof String json) || json.isBlank()) continue;
                try {
                    HealthRecord record = JSON.parseObject(json, HealthRecord.class);
                    if (record != null) records.add(record);
                } catch (Exception ignored) {
                    // skip malformed snapshot entries
                }
            }
            return records;
        } catch (Exception ex) {
            log.warn("按实时窗口读取 Redis 快照失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public void rememberLatest(HealthRecord record) {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        rememberLatest(current == null ? HealthDataSourceKey.NEW : current, record);
    }

    private void rememberLatest(HealthDataSourceKey source, HealthRecord record) {
        if (record == null) return;
        String userCode = record.getUserCode();
        if (userCode == null || userCode.isBlank()) return;
        try {
            String key = latestKey(source);
            Object previous = redisTemplate.opsForHash().get(key, userCode);
            HealthRecord merged = mergeLatestRecord(previous, record);
            redisTemplate.opsForHash().put(key, userCode, JSON.toJSONString(merged));
            redisTemplate.opsForZSet().add(onlineKey(source), userCode, recordEpochMillis(record));
            redisTemplate.expire(key, LATEST_RETENTION);
            redisTemplate.expire(onlineKey(source), LATEST_RETENTION);
        } catch (Exception ex) {
            log.debug("实时最新快照写入跳过: {}", ex.getMessage());
        }
    }

    private HealthRecord mergeLatestRecord(Object previousJson, HealthRecord incoming) {
        HealthRecord merged = null;
        if (previousJson instanceof String json && !json.isBlank()) {
            try {
                merged = JSON.parseObject(json, HealthRecord.class);
            } catch (Exception ignored) {
                // fall through to a fresh snapshot
            }
        }
        if (merged == null) {
            merged = new HealthRecord();
            merged.setUserCode(incoming.getUserCode());
        }

        Map<String, String> metricTimes = new LinkedHashMap<>();
        if (merged.getMetricTimes() != null) metricTimes.putAll(merged.getMetricTimes());
        seedLegacyMetricTimes(merged, metricTimes);
        String incomingTime = incoming.getTime() == null || incoming.getTime().isBlank()
                ? LocalDateTime.now().format(RECORD_TIME) : incoming.getTime();

        mergeMetric(merged, incoming, "heartRate", incomingTime, metricTimes,
                HealthRecord::getHeartRate, HealthRecord::setHeartRate);
        mergeMetric(merged, incoming, "bloodOxygen", incomingTime, metricTimes,
                HealthRecord::getBloodOxygen, HealthRecord::setBloodOxygen);
        mergeMetric(merged, incoming, "temperature", incomingTime, metricTimes,
                HealthRecord::getTemperature, HealthRecord::setTemperature);
        mergeMetric(merged, incoming, "bloodPressureHigh", incomingTime, metricTimes,
                HealthRecord::getBloodPressureHigh, HealthRecord::setBloodPressureHigh);
        mergeMetric(merged, incoming, "bloodPressureLow", incomingTime, metricTimes,
                HealthRecord::getBloodPressureLow, HealthRecord::setBloodPressureLow);
        mergeMetric(merged, incoming, "pressure", incomingTime, metricTimes,
                HealthRecord::getPressure, HealthRecord::setPressure);
        mergeMetric(merged, incoming, "steps", incomingTime, metricTimes,
                HealthRecord::getSteps, HealthRecord::setSteps);
        mergeMetric(merged, incoming, "calories", incomingTime, metricTimes,
                HealthRecord::getCalories, HealthRecord::setCalories);
        mergeMetric(merged, incoming, "sleepMinutes", incomingTime, metricTimes,
                HealthRecord::getSleepMinutes, HealthRecord::setSleepMinutes);
        merged.setMetricTimes(metricTimes);
        if (merged.getTime() == null || incomingTime.compareTo(merged.getTime()) >= 0) {
            merged.setTime(incomingTime);
        }
        return merged;
    }

    private <T> void mergeMetric(HealthRecord target, HealthRecord incoming, String key,
                                 String incomingTime, Map<String, String> metricTimes,
                                 Function<HealthRecord, T> getter, BiConsumer<HealthRecord, T> setter) {
        T value = getter.apply(incoming);
        if (value == null) return;
        String previousTime = metricTimes.get(key);
        if (previousTime == null || incomingTime.compareTo(previousTime) >= 0) {
            setter.accept(target, value);
            metricTimes.put(key, incomingTime);
        }
    }

    private void seedLegacyMetricTimes(HealthRecord record, Map<String, String> metricTimes) {
        String time = record.getTime();
        if (time == null || time.isBlank()) return;
        if (record.getHeartRate() != null) metricTimes.putIfAbsent("heartRate", time);
        if (record.getBloodOxygen() != null) metricTimes.putIfAbsent("bloodOxygen", time);
        if (record.getTemperature() != null) metricTimes.putIfAbsent("temperature", time);
        if (record.getBloodPressureHigh() != null) metricTimes.putIfAbsent("bloodPressureHigh", time);
        if (record.getBloodPressureLow() != null) metricTimes.putIfAbsent("bloodPressureLow", time);
        if (record.getPressure() != null) metricTimes.putIfAbsent("pressure", time);
        if (record.getSteps() != null) metricTimes.putIfAbsent("steps", time);
        if (record.getCalories() != null) metricTimes.putIfAbsent("calories", time);
        if (record.getSleepMinutes() != null) metricTimes.putIfAbsent("sleepMinutes", time);
    }

    private double recordEpochMillis(HealthRecord record) {
        if (record != null && record.getTime() != null && !record.getTime().isBlank()) {
            try {
                return LocalDateTime.parse(record.getTime().trim().replace('T', ' ').substring(0, 19), RECORD_TIME)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // use receive time below
            }
        }
        return System.currentTimeMillis();
    }

    @Scheduled(fixedDelay = 60_000)
    public void pruneRealtimeSnapshots() {
        double cutoff = System.currentTimeMillis() - LATEST_RETENTION.toMillis();
        for (HealthDataSourceKey source : HealthDataSourceKey.values()) {
            try {
                ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
                Set<String> staleUsers = zSet.rangeByScore(onlineKey(source), 0, cutoff);
                if (staleUsers == null || staleUsers.isEmpty()) continue;
                zSet.remove(onlineKey(source), staleUsers.toArray());
                redisTemplate.opsForHash().delete(latestKey(source), staleUsers.toArray());
            } catch (Exception ex) {
                log.debug("清理过期实时快照失败: source={}, error={}", source.key(), ex.getMessage());
            }
        }
    }

    private String latestKey(HealthDataSourceKey source) {
        return LATEST_KEY_PREFIX + source.key();
    }

    private String onlineKey(HealthDataSourceKey source) {
        return ONLINE_KEY_PREFIX + source.key();
    }

    private String currentBufferKey() {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        return bufferKey(current == null ? HealthDataSourceKey.NEW : current);
    }

    private String bufferKey(HealthDataSourceKey source) {
        return BUFFER_KEY_PREFIX + source.key();
    }

    private String deadLetterKey(HealthDataSourceKey source) {
        return DEAD_LETTER_KEY_PREFIX + source.key();
    }

    private String retryKey(HealthDataSourceKey source) {
        return RETRY_KEY_PREFIX + source.key();
    }

    private ParsedBatch parseBatch(HealthDataSourceKey source, List<String> items) {
        List<HealthRecord> records = new ArrayList<>();
        List<BadRecord> badEntries = new ArrayList<>();
        for (String item : items) {
            try {
                records.add(JSON.parseObject(item, HealthRecord.class));
            } catch (Exception ex) {
                badEntries.add(new BadRecord(item, ex.getMessage()));
                log.warn("健康数据反序列化失败明细: source={}, raw={}", source.key(), item);
            }
        }
        return new ParsedBatch(records, badEntries);
    }

    private void isolateBadRecords(HealthDataSourceKey source, String bufferKey, List<BadRecord> badEntries) {
        for (BadRecord badEntry : badEntries) {
            redisTemplate.opsForList().rightPush(deadLetterKey(source), JSON.toJSONString(Map.of(
                    "source", source.key(),
                    "failedAt", Instant.now().toString(),
                    "error", badEntry.errorMessage(),
                    "raw", badEntry.rawJson()
            )));
            redisTemplate.opsForList().remove(bufferKey, 1, badEntry.rawJson());
        }
    }

    private long incrementRetryCount(HealthDataSourceKey source) {
        Long value = redisTemplate.opsForValue().increment(retryKey(source));
        return value == null ? 1L : value;
    }

    private void clearRetryCount(HealthDataSourceKey source) {
        redisTemplate.delete(retryKey(source));
    }

    private record ParsedBatch(List<HealthRecord> records, List<BadRecord> badEntries) {
    }

    private record BadRecord(String rawJson, String errorMessage) {
    }
}
