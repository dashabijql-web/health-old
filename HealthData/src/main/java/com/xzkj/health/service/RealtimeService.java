package com.xzkj.health.service;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.dto.realtime.RealtimeAlertRow;
import com.xzkj.health.dto.realtime.RealtimeAlertView;
import com.xzkj.health.dto.realtime.RealtimeHealthMetricView;
import com.xzkj.health.dto.realtime.RealtimeHealthSnapshotView;
import com.xzkj.health.dto.realtime.RealtimeMonitorSummaryView;
import com.xzkj.health.dto.realtime.RealtimeOverviewRow;
import com.xzkj.health.dto.realtime.RealtimeOverviewView;
import com.xzkj.health.dto.realtime.RealtimeStatisticsRow;
import com.xzkj.health.dto.realtime.RealtimeStatisticsView;
import com.xzkj.health.dto.realtime.RealtimeUserDetailRow;
import com.xzkj.health.dto.realtime.RealtimeUserDetailView;
import com.xzkj.health.dto.realtime.RealtimeUserPageView;
import com.xzkj.health.dto.realtime.RealtimeUserRow;
import com.xzkj.health.dto.realtime.RealtimeUserView;
import com.xzkj.health.mapper.RealtimeMapper;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.model.entity.AlertConfig;
import com.xzkj.health.util.LocalTtlCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RealtimeService {

    private static final long STATISTICS_TTL = 30_000L;
    private static final long ONLINE_USERS_TTL = 10_000L;
    private static final long DIRECTORY_TTL = 30_000L;
    private static final int WARNING_PREVIEW_SIZE = 12;
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RealtimeMapper realtimeMapper;
    private final AlertConfigService alertConfigService;
    private final RedisHealthBufferService redisHealthBufferService;
    private final LocalTtlCache<RealtimeStatisticsView> statisticsCache = new LocalTtlCache<>();
    private final LocalTtlCache<Map<String, RealtimeUserRow>> directoryCache = new LocalTtlCache<>();
    private final ConcurrentHashMap<String, CacheEntry<RealtimeSnapshot>> snapshotCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> snapshotLocks = new ConcurrentHashMap<>();
    private volatile RealtimeStatisticsView lastStatistics;

    @Value("${health.realtime.online-window-minutes:15}")
    private int onlineWindowMinutes = 15;

    @Value("${health.realtime.freshness-minutes:5}")
    private int freshnessMinutes = 5;

    public RealtimeService(RealtimeMapper realtimeMapper,
                           AlertConfigService alertConfigService,
                           RedisHealthBufferService redisHealthBufferService) {
        this.realtimeMapper = realtimeMapper;
        this.alertConfigService = alertConfigService;
        this.redisHealthBufferService = redisHealthBufferService;
    }

    public RealtimeOverviewView getTodayAvgOverview() {
        RealtimeOverviewRow data = realtimeMapper.getTodayAvgData();
        if (data == null) data = new RealtimeOverviewRow();

        double avgTemp = doubleValue(data.getAvgTemperature());
        double avgSleep = doubleValue(data.getAvgSleep());
        return new RealtimeOverviewView(
                Math.round(doubleValue(data.getAvgHeartRate())),
                Math.round(doubleValue(data.getAvgBloodOxygen())),
                Math.round(doubleValue(data.getAvgSteps())),
                Math.round(avgTemp * 10.0) / 10.0,
                Math.round(avgSleep * 10.0) / 10.0,
                longValue(data.getTodayWarningCount())
        );
    }

    private static String onlineUsersTableSource(int onlineWindowMinutes) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMM");
        String current = LocalDateTime.now().format(fmt);
        String previous = LocalDateTime.now().minusMinutes(Math.max(1, onlineWindowMinutes)).format(fmt);
        if (current.equals(previous)) {
            return "health_record_" + current;
        }
        String cols = "id,user_code,heart_rate,blood_oxygen,temperature,steps,calories," +
                "sleep_minutes,blood_pressure_high,blood_pressure_low,pressure,record_time";
        return "(SELECT " + cols + " FROM health_record_" + previous +
                " UNION ALL SELECT " + cols + " FROM health_record_" + current + ")";
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public RealtimeUserPageView getOnlineUsers(Integer page, Integer size,
                                               String name, String dept, String status) {
        return getOnlineUsers(page, size, name, dept, status, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public RealtimeUserPageView getOnlineUsers(Integer page, Integer size,
                                               String name, String dept, String status,
                                               String indicator) {
        int safePage = Math.max(1, page == null ? 1 : page);
        int safeSize = Math.max(1, DateParamUtil.clampSize(size == null ? 50 : size, 200));
        int safeOnlineWindow = Math.max(1, onlineWindowMinutes);
        int safeFreshness = Math.max(1, Math.min(freshnessMinutes, safeOnlineWindow));
        String safeIndicator = validateIndicator(indicator);
        SnapshotLookup lookup = lookupRealtimeSnapshot(safeOnlineWindow, safeFreshness, true);
        return pageSnapshot(lookup.snapshot(), safePage, safeSize, name, dept, status, safeIndicator, lookup.stale());
    }

    private SnapshotLookup lookupRealtimeSnapshot(int onlineWindow, int freshnessWindow, boolean required) {
        String cacheKey = HealthCacheKeys.key("online-users-snapshot", onlineWindow, freshnessWindow);
        long now = System.currentTimeMillis();
        CacheEntry<RealtimeSnapshot> cached = snapshotCache.get(cacheKey);
        if (cached != null && now < cached.expireAt()) {
            return new SnapshotLookup(cached.value(), false);
        }
        Object lock = snapshotLocks.computeIfAbsent(cacheKey, key -> new Object());
        synchronized (lock) {
            now = System.currentTimeMillis();
            cached = snapshotCache.get(cacheKey);
            if (cached != null && now < cached.expireAt()) {
                return new SnapshotLookup(cached.value(), false);
            }
            try {
                RealtimeSnapshot snapshot = loadRealtimeSnapshot(onlineWindow, freshnessWindow);
                snapshotCache.put(cacheKey, new CacheEntry<>(snapshot, System.currentTimeMillis() + ONLINE_USERS_TTL));
                return new SnapshotLookup(snapshot, false);
            } catch (Exception ex) {
                log.warn("实时监控快照查询失败，尝试返回过期缓存: {}", ex.getMessage());
                if (cached != null) {
                    return new SnapshotLookup(cached.value(), true);
                }
                if (required) {
                    throw new BusinessException(503, "实时监控数据加载失败，请稍后重试");
                }
                return new SnapshotLookup(null, false);
            }
        }
    }

    private RealtimeSnapshot loadRealtimeSnapshot(int onlineWindow, int freshnessWindow) {
        RedisLatestLookup redisLookup = loadRowsFromRedis(onlineWindow);
        List<RealtimeUserRow> rows;
        if (redisLookup.populated()) {
            rows = redisLookup.rows();
        } else {
            rows = realtimeMapper.getActiveUsersDirect(onlineUsersTableSource(onlineWindow), onlineWindow);
            if (rows == null) rows = Collections.emptyList();
            seedRedisLatest(rows);
        }

        Map<Integer, Map<Integer, AlertConfig>> configByRisk = new HashMap<>();
        List<RealtimeUserView> users = new ArrayList<>(rows.size());
        for (RealtimeUserRow row : rows) {
            if (row == null) continue;
            Integer riskLevel = row.getRiskLevel();
            Map<Integer, AlertConfig> configs = configByRisk.computeIfAbsent(riskLevel, this::loadAlertConfigs);
            users.add(toRealtimeUserView(row, configs, freshnessWindow));
        }

        users.sort(realtimeUserComparator());
        RealtimeMonitorSummaryView summary = summarize(users, onlineWindow, freshnessWindow);
        Set<String> departmentNames = new LinkedHashSet<>();
        for (RealtimeUserView user : users) {
            if (user.deptName() != null && !user.deptName().isBlank()) departmentNames.add(user.deptName());
        }
        List<String> departments = departmentNames.stream().sorted().toList();
        List<RealtimeUserView> warningPreview = users.stream()
                .filter(user -> "warning".equals(user.status()))
                .limit(WARNING_PREVIEW_SIZE)
                .toList();

        return new RealtimeSnapshot(
                List.copyOf(users),
                summary,
                departments,
                warningPreview,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private RedisLatestLookup loadRowsFromRedis(int onlineWindow) {
        if (redisHealthBufferService == null) return RedisLatestLookup.missing();
        List<HealthRecord> latest;
        try {
            latest = redisHealthBufferService.latestRecords(onlineWindow);
        } catch (Exception ex) {
            log.warn("实时监控读取 Redis 快照失败: {}", ex.getMessage());
            return RedisLatestLookup.missing();
        }
        if (latest == null || latest.isEmpty()) return RedisLatestLookup.missing();

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, onlineWindow));
        List<HealthRecord> inWindow = new ArrayList<>();
        List<LocalDateTime> inWindowTimes = new ArrayList<>();
        for (HealthRecord record : latest) {
            if (record == null || record.getUserCode() == null || record.getUserCode().isBlank()) continue;
            LocalDateTime recordTime = parseRecordTime(record.getTime());
            if (recordTime == null || recordTime.isBefore(cutoff)) continue;
            inWindow.add(record);
            inWindowTimes.add(recordTime);
        }
        if (inWindow.isEmpty()) return RedisLatestLookup.from(List.of());

        Map<String, RealtimeUserRow> directory = loadEmployeeDirectory();
        List<RealtimeUserRow> rows = new ArrayList<>(inWindow.size());
        for (int i = 0; i < inWindow.size(); i++) {
            HealthRecord record = inWindow.get(i);
            rows.add(toRealtimeUserRow(directory.get(record.getUserCode()), record, inWindowTimes.get(i)));
        }
        return RedisLatestLookup.from(rows);
    }

    private Map<String, RealtimeUserRow> loadEmployeeDirectory() {
        Map<String, RealtimeUserRow> cached = directoryCache.getIfFresh("dir");
        if (cached != null) return cached;
        try {
            List<RealtimeUserRow> directory = realtimeMapper.getOnlineEmployeeDirectory();
            if (directory == null || directory.isEmpty()) return Map.of();
            Map<String, RealtimeUserRow> byCode = new HashMap<>();
            for (RealtimeUserRow row : directory) {
                if (row != null && row.getUserCode() != null && !row.getUserCode().isBlank()) {
                    byCode.putIfAbsent(row.getUserCode(), row);
                }
            }
            directoryCache.put("dir", byCode, DIRECTORY_TTL);
            return byCode;
        } catch (Exception ex) {
            log.warn("实时监控人员目录加载失败: {}", ex.getMessage());
            return Map.of();
        }
    }

    private void seedRedisLatest(List<RealtimeUserRow> rows) {
        if (redisHealthBufferService == null || rows == null || rows.isEmpty()) return;
        for (RealtimeUserRow row : rows) {
            if (row == null || row.getUserCode() == null || row.getUserCode().isBlank()) continue;
            HealthRecord record = new HealthRecord();
            record.setUserCode(row.getUserCode());
            record.setHeartRate(row.getHeartRate());
            record.setBloodOxygen(row.getBloodOxygen());
            if (row.getTemperature() != null) {
                record.setTemperature((int) Math.round(row.getTemperature() * 10.0));
            }
            record.setBloodPressureHigh(row.getBloodPressureHigh());
            record.setBloodPressureLow(row.getBloodPressureLow());
            record.setPressure(row.getPressure());
            record.setSteps(row.getSteps());
            record.setCalories(row.getCalories());
            if (row.getSleepHours() != null) {
                record.setSleepMinutes((int) Math.round(row.getSleepHours() * 60.0));
            }
            record.setTime(row.getLastUpdate());
            redisHealthBufferService.rememberLatest(record);
        }
    }

    private RealtimeUserRow toRealtimeUserRow(RealtimeUserRow directory, HealthRecord record, LocalDateTime recordTime) {
        RealtimeUserRow row = new RealtimeUserRow();
        if (directory != null) {
            row.setId(directory.getId());
            row.setUserCode(directory.getUserCode());
            row.setUserName(directory.getUserName());
            row.setGender(directory.getGender());
            row.setAge(directory.getAge());
            row.setDeptName(directory.getDeptName());
            row.setRiskLevel(directory.getRiskLevel());
            row.setImei(directory.getImei());
        } else {
            row.setUserCode(record.getUserCode());
            row.setUserName(record.getUserCode());
        }
        row.setHeartRate(record.getHeartRate());
        row.setBloodOxygen(record.getBloodOxygen());
        row.setSteps(record.getSteps());
        row.setCalories(record.getCalories());
        if (record.getTemperature() != null) {
            row.setTemperature(record.getTemperature() / 10.0);
        }
        if (record.getSleepMinutes() != null) {
            row.setSleepHours(record.getSleepMinutes() / 60.0);
        }
        row.setBloodPressureHigh(record.getBloodPressureHigh());
        row.setBloodPressureLow(record.getBloodPressureLow());
        row.setPressure(record.getPressure());
        Map<String, String> metricTimes = record.getMetricTimes() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(record.getMetricTimes());
        seedLegacyMetricTimes(record, metricTimes);
        row.setMetricTimes(Map.copyOf(metricTimes));
        String lastUpdate = recordTime.format(RECORD_TIME);
        boolean hasVital = record.getHeartRate() != null || record.getBloodOxygen() != null
                || record.getTemperature() != null || record.getBloodPressureHigh() != null
                || record.getBloodPressureLow() != null || record.getPressure() != null;
        row.setLastUpdate(hasVital ? lastUpdate : null);
        row.setDataAgeSeconds(hasVital ? Math.max(0L, java.time.Duration.between(recordTime, LocalDateTime.now()).toSeconds()) : null);
        return row;
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

    private LocalDateTime parseRecordTime(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() > 19) normalized = normalized.substring(0, 19);
        try {
            return LocalDateTime.parse(normalized, RECORD_TIME);
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public RealtimeHealthSnapshotView getHealthSnapshot() {
        int safeOnlineWindow = Math.max(1, onlineWindowMinutes);
        int safeFreshness = Math.max(1, Math.min(freshnessMinutes, safeOnlineWindow));
        RealtimeSnapshot snapshot = lookupRealtimeSnapshot(safeOnlineWindow, safeFreshness, false).snapshot();
        if (snapshot == null) {
            return new RealtimeHealthSnapshotView(
                    "NO_DATA",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    safeOnlineWindow,
                    safeFreshness,
                    0, 0, 0, 0, 0, 0d,
                    List.of());
        }
        List<RealtimeUserView> freshUsers = snapshot.users().stream()
                .filter(user -> "normal".equals(user.status()) || "warning".equals(user.status()))
                .toList();

        List<RealtimeHealthMetricView> metrics = List.of(
                buildHealthMetric("heartRate", "心率", "bpm", freshUsers,
                        user -> number(user.heartRate())),
                buildHealthMetric("bloodOxygen", "血氧", "%", freshUsers,
                        user -> number(user.bloodOxygen())),
                buildHealthMetric("temperature", "体温", "°C", freshUsers,
                        RealtimeUserView::temperature),
                buildHealthMetric("pressure", "压力", "idx", freshUsers,
                        user -> number(user.pressure())),
                buildHealthMetric("bloodPressureHigh", "高压", "mmHg", freshUsers,
                        user -> number(user.bloodPressureHigh())),
                buildHealthMetric("bloodPressureLow", "低压", "mmHg", freshUsers,
                        user -> number(user.bloodPressureLow()))
        );

        int onlineUsers = snapshot.summary().onlineCount();
        int freshCount = freshUsers.size();
        String status = onlineUsers == 0 ? "NO_DATA"
                : freshCount == 0 ? "STALE"
                : freshCount < onlineUsers ? "PARTIAL" : "NORMAL";
        double coverageRate = onlineUsers == 0 ? 0d : roundOne(freshCount * 100d / onlineUsers);

        return new RealtimeHealthSnapshotView(
                status,
                snapshot.refreshedAt(),
                safeOnlineWindow,
                safeFreshness,
                onlineUsers,
                freshCount,
                snapshot.summary().warningCount(),
                snapshot.summary().staleCount(),
                snapshot.summary().noDataCount(),
                coverageRate,
                metrics
        );
    }

    private RealtimeHealthMetricView buildHealthMetric(
            String key,
            String label,
            String unit,
            List<RealtimeUserView> users,
            Function<RealtimeUserView, Double> valueExtractor) {
        List<Double> values = users.stream()
                .filter(user -> !"stale".equals(user.indicatorStates().get(key)))
                .map(valueExtractor)
                .filter(value -> value != null && value > 0)
                .sorted()
                .toList();
        int abnormalUsers = (int) users.stream()
                .filter(user -> isAbnormalState(user.indicatorStates().get(key)))
                .count();
        int coveredUsers = values.size();
        double abnormalRate = coveredUsers == 0 ? 0d : roundOne(abnormalUsers * 100d / coveredUsers);
        if (values.isEmpty()) {
            return new RealtimeHealthMetricView(key, label, unit, null, null, null, null,
                    0, abnormalUsers, abnormalRate);
        }
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        int p95Index = Math.max(0, (int) Math.ceil(values.size() * 0.95d) - 1);
        return new RealtimeHealthMetricView(
                key, label, unit,
                roundOne(average),
                roundOne(values.get(0)),
                roundOne(values.get(values.size() - 1)),
                roundOne(values.get(p95Index)),
                coveredUsers,
                abnormalUsers,
                abnormalRate
        );
    }

    private boolean isAbnormalState(String state) {
        return "warning".equals(state) || "danger".equals(state);
    }

    private Double number(Number value) {
        return value == null ? null : value.doubleValue();
    }

    private double roundOne(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private Map<Integer, AlertConfig> loadAlertConfigs(Integer riskLevel) {
        try {
            return alertConfigService.getConfigMap(riskLevel);
        } catch (Exception ex) {
            log.warn("实时监控阈值配置加载失败，使用内置兜底: riskLevel={}, error={}", riskLevel, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private RealtimeUserPageView pageSnapshot(RealtimeSnapshot snapshot, int page, int size,
                                              String name, String dept, String status,
                                              String indicator, boolean stale) {
        String keyword = normalize(name);
        String department = normalize(dept);
        String state = normalize(status);

        List<RealtimeUserView> filtered = snapshot.users().stream()
                .filter(user -> matchesKeyword(user, keyword))
                .filter(user -> department.isEmpty() || department.equalsIgnoreCase(stringValue(user.deptName())))
                .filter(user -> state.isEmpty() || state.equalsIgnoreCase(user.status()))
                .filter(user -> indicator.isEmpty()
                        || isAbnormalState(user.indicatorStates().get(indicator)))
                .toList();

        int total = filtered.size();
        int maxPage = Math.max(1, (int) Math.ceil(total / (double) size));
        int boundedPage = Math.min(page, maxPage);
        int start = Math.min((boundedPage - 1) * size, total);
        int end = Math.min(start + size, total);

        return new RealtimeUserPageView(
                filtered.subList(start, end),
                total,
                boundedPage,
                size,
                stale,
                snapshot.refreshedAt(),
                snapshot.summary(),
                snapshot.departments(),
                snapshot.warningPreview()
        );
    }

    private String validateIndicator(String indicator) {
        String value = indicator == null ? "" : indicator.trim();
        if (value.isEmpty()) return "";
        if (Set.of("heartRate", "bloodOxygen", "temperature", "pressure",
                "bloodPressureHigh", "bloodPressureLow").contains(value)) {
            return value;
        }
        throw new BusinessException(400, "不支持的实时体征指标");
    }

    private boolean matchesKeyword(RealtimeUserView user, String keyword) {
        if (keyword.isEmpty()) return true;
        return stringValue(user.userName()).toLowerCase(Locale.ROOT).contains(keyword)
                || stringValue(user.userCode()).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private RealtimeUserView toRealtimeUserView(RealtimeUserRow row, Map<Integer, AlertConfig> configs,
                                                int freshnessWindow) {
        long ageSeconds = Math.max(0L, longValue(row.getDataAgeSeconds()));
        MetricEvaluation evaluation = evaluateMetrics(row, configs, freshnessWindow);
        boolean noData = row.getHeartRate() == null
                && row.getBloodOxygen() == null
                && row.getTemperature() == null
                && row.getBloodPressureHigh() == null
                && row.getBloodPressureLow() == null
                && row.getPressure() == null;

        String status;
        String severity;
        if (noData) {
            status = "no_data";
            severity = "info";
        } else if (ageSeconds > freshnessWindow * 60L) {
            status = "stale";
            severity = "info";
        } else if (!evaluation.reasons().isEmpty()) {
            status = "warning";
            severity = evaluation.severity();
        } else {
            status = "normal";
            severity = "normal";
        }

        return new RealtimeUserView(
                longValue(row.getId()),
                stringValue(row.getUserCode()),
                stringValue(row.getUserName()),
                intValue(row.getGender()),
                intValue(row.getAge()),
                stringValue(row.getDeptName()),
                intValue(row.getHeartRate()),
                intValue(row.getBloodOxygen()),
                intValue(row.getSteps()),
                intValue(row.getCalories()),
                roundNullableDouble(row.getTemperature()),
                roundNullableDouble(row.getSleepHours()),
                intValue(row.getBloodPressureHigh()),
                intValue(row.getBloodPressureLow()),
                intValue(row.getPressure()),
                status,
                severity,
                evaluation.reasons(),
                evaluation.indicatorStates(),
                row.getMetricTimes() == null ? Map.of() : Map.copyOf(row.getMetricTimes()),
                stringValue(row.getLastUpdate()),
                ageSeconds,
                stringValue(row.getImei())
        );
    }

    private MetricEvaluation evaluateMetrics(RealtimeUserRow row, Map<Integer, AlertConfig> configs,
                                             int freshnessWindow) {
        List<String> reasons = new ArrayList<>();
        Map<String, String> states = new LinkedHashMap<>();
        int severityRank = 0;

        severityRank = evaluateRange("heartRate", "心率", row.getHeartRate(), "bpm", configs.get(1),
                new Thresholds(60d, 100d, 50d, 120d), reasons, states, severityRank);
        severityRank = evaluateRange("bloodOxygen", "血氧", row.getBloodOxygen(), "%", configs.get(2),
                new Thresholds(95d, null, 90d, null), reasons, states, severityRank);
        severityRank = evaluateRange("temperature", "体温", row.getTemperature(), "°C", configs.get(3),
                new Thresholds(36d, 37.5d, 35d, 38d), reasons, states, severityRank);
        severityRank = evaluateRange("bloodPressureHigh", "收缩压", row.getBloodPressureHigh(), "mmHg", configs.get(4),
                new Thresholds(90d, 139d, null, 180d), reasons, states, severityRank);
        severityRank = evaluateRange("bloodPressureLow", "舒张压", row.getBloodPressureLow(), "mmHg", null,
                new Thresholds(60d, 89d, null, 120d), reasons, states, severityRank);
        severityRank = evaluateRange("pressure", "压力指数", row.getPressure(), "", configs.get(5),
                new Thresholds(null, 84d, null, 90d), reasons, states, severityRank);

        markStaleIndicators(row.getMetricTimes(), freshnessWindow, states, reasons);

        String severity = states.containsValue("danger")
                ? "danger"
                : states.containsValue("warning") ? "warning" : "normal";
        return new MetricEvaluation(List.copyOf(reasons), Map.copyOf(states), severity);
    }

    private void markStaleIndicators(Map<String, String> metricTimes, int freshnessWindow,
                                     Map<String, String> states, List<String> reasons) {
        if (metricTimes == null || metricTimes.isEmpty()) return;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, freshnessWindow));
        for (Map.Entry<String, String> entry : metricTimes.entrySet()) {
            LocalDateTime collectedAt = parseRecordTime(entry.getValue());
            if (collectedAt == null || !collectedAt.isBefore(cutoff)) continue;
            states.put(entry.getKey(), "stale");
        }
        if (states.containsValue("stale")) {
            reasons.removeIf(reason -> matchesStaleReason(reason, states));
        }
    }

    private boolean matchesStaleReason(String reason, Map<String, String> states) {
        if (reason == null) return false;
        return ("stale".equals(states.get("heartRate")) && reason.startsWith("心率 "))
                || ("stale".equals(states.get("bloodOxygen")) && reason.startsWith("血氧 "))
                || ("stale".equals(states.get("temperature")) && reason.startsWith("体温 "))
                || ("stale".equals(states.get("bloodPressureHigh")) && reason.startsWith("收缩压 "))
                || ("stale".equals(states.get("bloodPressureLow")) && reason.startsWith("舒张压 "))
                || ("stale".equals(states.get("pressure")) && reason.startsWith("压力指数 "));
    }

    private int evaluateRange(String key, String label, Number value, String unit, AlertConfig config,
                              Thresholds fallback, List<String> reasons, Map<String, String> states,
                              int currentRank) {
        if (value == null || (config != null && !Integer.valueOf(1).equals(config.getEnabled()))) return currentRank;

        double numeric = value.doubleValue();
        double warnLow = threshold(config == null ? null : config.getWarnLow(), fallback.warnLow());
        double warnHigh = threshold(config == null ? null : config.getWarnHigh(), fallback.warnHigh());
        double criticalLow = threshold(config == null ? null : config.getCriticalLow(), fallback.criticalLow());
        double criticalHigh = threshold(config == null ? null : config.getCriticalHigh(), fallback.criticalHigh());
        double midLow = threshold(config == null ? null : config.getWarnMidLow(), criticalLow);
        double midHigh = threshold(config == null ? null : config.getWarnMidHigh(), criticalHigh);

        int rank = outside(numeric, criticalLow, criticalHigh) ? 3
                : outside(numeric, midLow, midHigh) ? 2
                : outside(numeric, warnLow, warnHigh) ? 1 : 0;
        if (rank == 0) return currentRank;

        String state = rank >= 2 ? "danger" : "warning";
        states.put(key, state);
        String formatted = value instanceof Double || value instanceof Float
                ? String.format(Locale.ROOT, "%.1f", numeric)
                : String.valueOf(value.longValue());
        reasons.add(label + " " + formatted + (unit.isEmpty() ? "" : " " + unit));
        return Math.max(currentRank, rank);
    }

    private boolean outside(double value, double low, double high) {
        return (!Double.isNaN(low) && value < low) || (!Double.isNaN(high) && value > high);
    }

    private double threshold(BigDecimal configured, Double fallback) {
        if (configured != null) return configured.doubleValue();
        return fallback == null ? Double.NaN : fallback;
    }

    private double threshold(BigDecimal configured, double fallback) {
        return configured == null ? fallback : configured.doubleValue();
    }

    private Comparator<RealtimeUserView> realtimeUserComparator() {
        return Comparator
                .comparingInt((RealtimeUserView user) -> statusRank(user.status()))
                .thenComparingInt(user -> severityRank(user.severity()))
                .thenComparing(RealtimeUserView::lastUpdate, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int statusRank(String status) {
        return switch (status) {
            case "warning" -> 0;
            case "stale", "no_data" -> 1;
            default -> 2;
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "danger" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private RealtimeMonitorSummaryView summarize(List<RealtimeUserView> users,
                                                 int onlineWindow, int freshnessWindow) {
        int normal = 0;
        int warning = 0;
        int stale = 0;
        int noData = 0;
        for (RealtimeUserView user : users) {
            switch (user.status()) {
                case "normal" -> normal++;
                case "warning" -> warning++;
                case "stale" -> stale++;
                case "no_data" -> noData++;
                default -> { }
            }
        }
        return new RealtimeMonitorSummaryView(users.size(), normal, warning, stale, noData,
                onlineWindow, freshnessWindow);
    }

    public RealtimeUserDetailView getUserRealtimeData(String userCode) {
        RealtimeUserDetailRow data = realtimeMapper.getUserRealtimeData(userCode);
        if (data == null) {
            return new RealtimeUserDetailView(userCode, "", "", null, null, null, null, null,
                    null, null, null, null, null, "offline");
        }
        return new RealtimeUserDetailView(
                stringValue(data.getUserCode()), stringValue(data.getUserName()), stringValue(data.getDeptName()),
                intValue(data.getHeartRate()), intValue(data.getBloodOxygen()), roundNullableDouble(data.getTemperature()),
                intValue(data.getBloodPressureHigh()), intValue(data.getBloodPressureLow()), intValue(data.getPressure()),
                intValue(data.getSteps()), intValue(data.getCalories()), roundNullableDouble(data.getSleepHours()),
                stringValue(data.getLastUpdate()), stringValue(data.getStatus())
        );
    }

    public RealtimeStatisticsView getStatistics() {
        String cacheKey = HealthCacheKeys.key("statistics");
        RealtimeStatisticsView hit = statisticsCache.getIfFresh(cacheKey);
        if (hit != null) return hit;

        try {
            RealtimeStatisticsRow data = realtimeMapper.getStatisticsDirect();
            if (data == null) data = new RealtimeStatisticsRow();
            long totalUsers = longValue(data.getTotalUsers());
            long onlineUsers = countOnlineFromRedis();
            if (onlineUsers <= 0) onlineUsers = longValue(data.getOnlineUsers());
            long onlineRate = totalUsers > 0 ? Math.round(onlineUsers * 100d / totalUsers) : 0L;
            RealtimeStatisticsView result = new RealtimeStatisticsView(
                    onlineUsers, totalUsers,
                    longValue(data.getWeekRecords()), longValue(data.getTodayRecords()),
                    onlineRate, Math.round(doubleValue(data.getNormalRate()))
            );
            statisticsCache.put(cacheKey, result, STATISTICS_TTL);
            lastStatistics = result;
            return result;
        } catch (Exception ex) {
            log.warn("实时统计查询失败，尝试返回缓存: {}", ex.getMessage());
            if (lastStatistics != null) return lastStatistics;
            return new RealtimeStatisticsView(0, 0, 0, 0, 0, 0);
        }
    }

    private long countOnlineFromRedis() {
        if (redisHealthBufferService == null) return 0L;
        List<HealthRecord> latest;
        try {
            latest = redisHealthBufferService.latestRecords(onlineWindowMinutes);
        } catch (Exception ex) {
            return 0L;
        }
        if (latest == null || latest.isEmpty()) return 0L;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, onlineWindowMinutes));
        long count = 0L;
        for (HealthRecord record : latest) {
            if (record == null || record.getUserCode() == null || record.getUserCode().isBlank()) continue;
            LocalDateTime recordTime = parseRecordTime(record.getTime());
            if (recordTime != null && !recordTime.isBefore(cutoff)) count++;
        }
        return count;
    }

    public List<RealtimeAlertView> getRealtimeAlerts(int limit) {
        List<RealtimeAlertRow> rows = realtimeMapper.getRecentAlerts(limit);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<RealtimeAlertView> result = new ArrayList<>();
        for (RealtimeAlertRow row : rows) {
            if (row == null) continue;
            Integer handled = intValue(row.getHandled());
            result.add(new RealtimeAlertView(
                    longValue(row.getId()), stringValue(row.getUserCode()), stringValue(row.getUserName()),
                    stringValue(row.getDeptName()), stringValue(row.getWarningType()),
                    stringValue(row.getIndicatorName()), stringValue(row.getIndicatorValue()),
                    stringValue(row.getWarningLevel()), handled != null && handled > 0,
                    stringValue(row.getCreateTime())
            ));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Integer intValue(Number value) {
        return value == null ? null : value.intValue();
    }

    private long longValue(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double doubleValue(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private Double roundNullableDouble(Number value) {
        return value == null ? null : Math.round(value.doubleValue() * 10.0) / 10.0;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CacheEntry<T>(T value, long expireAt) { }

    private record SnapshotLookup(RealtimeSnapshot snapshot, boolean stale) { }

    private record RedisLatestLookup(boolean populated, List<RealtimeUserRow> rows) {
        static RedisLatestLookup missing() {
            return new RedisLatestLookup(false, List.of());
        }

        static RedisLatestLookup from(List<RealtimeUserRow> rows) {
            return new RedisLatestLookup(true, rows == null ? List.of() : rows);
        }
    }

    private record RealtimeSnapshot(
            List<RealtimeUserView> users,
            RealtimeMonitorSummaryView summary,
            List<String> departments,
            List<RealtimeUserView> warningPreview,
            String refreshedAt
    ) { }

    private record MetricEvaluation(
            List<String> reasons,
            Map<String, String> indicatorStates,
            String severity
    ) { }

    private record Thresholds(Double warnLow, Double warnHigh, Double criticalLow, Double criticalHigh) { }
}
