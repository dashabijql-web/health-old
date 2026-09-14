package com.xzkj.health.service;

import com.xzkj.health.common.MapValueUtil;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.dto.riskwarning.RiskWarningDeptStatView;
import com.xzkj.health.dto.riskwarning.RiskWarningItemView;
import com.xzkj.health.dto.riskwarning.RiskWarningLocatorRequest;
import com.xzkj.health.dto.riskwarning.RiskWarningOverviewView;
import com.xzkj.health.dto.riskwarning.RiskWarningPageView;
import com.xzkj.health.dto.riskwarning.RiskWarningTrendSeriesView;
import com.xzkj.health.dto.riskwarning.RiskWarningTrendView;
import com.xzkj.health.dto.riskwarning.RiskWarningTypeCountView;
import com.xzkj.health.mapper.RiskWarningMapper;
import com.xzkj.health.observability.HealthMetricsService;
import com.xzkj.health.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

/**
 * 风险预警Service
 *
 * 分表适配（v2）：
 *   - 读取：Mapper 已改为查 v_warning_record 视图，此层无需改动
 *   - 处理单条/批量预警：
 *     先从视图查出 create_time，计算对应月份表，再更新该月份表
 *   - 插入新预警：insertWarning() 写入当前月份表
 */
@Slf4j
@Service
public class RiskWarningService {

    private static final DateTimeFormatter DT_FMT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private RiskWarningMapper riskWarningMapper;

    @Autowired
    private HealthMetricsService healthMetricsService;

    // ─── 读取 ─────────────────────────────────────────────────────────

    public RiskWarningOverviewView getWarningStats(String startDate, String endDate) {
        Map<String, Object> stats = riskWarningMapper.getWarningOverview(startDate, endDate);
        int handledRate = stats != null && stats.containsKey("handledRate")
                ? MapValueUtil.getInt(stats, "handledRate")
                : calculateHandledRate(stats);
        return new RiskWarningOverviewView(
                MapValueUtil.getInt(stats, "heartRateCount"),
                MapValueUtil.getInt(stats, "sleepCount"),
                MapValueUtil.getInt(stats, "bloodOxygenCount"),
                MapValueUtil.getInt(stats, "temperatureCount"),
                MapValueUtil.getInt(stats, "pressureCount"),
                MapValueUtil.getInt(stats, "totalWarnings"),
                MapValueUtil.getInt(stats, "handledWarnings"),
                MapValueUtil.getInt(stats, "pendingWarnings"),
                MapValueUtil.getInt(stats, "dangerCount"),
                MapValueUtil.getInt(stats, "warningCount"),
                handledRate
        );
    }

    public RiskWarningPageView getWarningList(String level, Boolean handled, String userCode, String keyword,
                                              String warningType, String eventSource, String eventCode,
                                              String startDate, String endDate,
                                              int page, int size) {
        if (shouldUseMonthlyTimeWindow(userCode, keyword, warningType, eventSource, eventCode, startDate, endDate)) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusDays(30);
            return getWarningListByTimeWindow(level, handled, start.format(SQL_DATE_TIME), end.format(SQL_DATE_TIME), page, size);
        }
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = riskWarningMapper.getWarningList(
                level, handled, userCode, keyword, warningType, eventSource, eventCode,
                startDate, endDate, offset, size);
        int total = riskWarningMapper.countWarnings(
                level, handled, userCode, keyword, warningType, eventSource, eventCode, startDate, endDate);

        return new RiskWarningPageView(toItemViews(list), total, page, size);
    }

    private boolean shouldUseMonthlyTimeWindow(String userCode, String keyword, String warningType,
                                               String eventSource, String eventCode,
                                               String startDate, String endDate) {
        return isBlank(userCode) && isBlank(keyword) && isBlank(warningType)
                && isBlank(eventSource) && isBlank(eventCode)
                && isBlank(startDate) && isBlank(endDate);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Command-center queries require exact timestamp windows. The existing list API is
     * date-oriented, so keep this path separate rather than silently widening realtime
     * incidents to a whole calendar day.
     */
    public RiskWarningPageView getWarningListByTimeWindow(
            String level, Boolean handled, String startAt, String endAt, int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = riskWarningMapper.getWarningListByTimeWindow(
                level, handled, startAt, endAt, offset, size);
        int total = riskWarningMapper.countWarningsByTimeWindow(level, handled, startAt, endAt);
        return new RiskWarningPageView(toItemViews(list), total, page, size);
    }

    public int countWarningsByTimeWindow(String level, Boolean handled, String startAt, String endAt) {
        return riskWarningMapper.countWarningsByTimeWindow(level, handled, startAt, endAt);
    }

    /**
     * Looks up one warning by its monthly-table-safe locator: id plus createTime.
     * A bare id is retained only as a backward-compatible fallback for legacy callers.
     */
    public RiskWarningItemView getWarningDetail(Long id, String createTime) {
        Map<String, Object> row = riskWarningMapper.getWarningDetail(id, createTime);
        if (row == null || row.isEmpty()) {
            return null;
        }
        return toItemViews(List.of(row)).get(0);
    }

    public RiskWarningTrendView getWarningTrend(int days) {
        List<Map<String, Object>> trendData = riskWarningMapper.getWarningTrendByType(days);

        List<String> dates = new ArrayList<>();
        List<Integer> heartRateData = new ArrayList<>();
        List<Integer> bloodOxygenData = new ArrayList<>();
        List<Integer> sleepData = new ArrayList<>();
        List<Integer> temperatureData = new ArrayList<>();
        List<Integer> pressureData = new ArrayList<>();

        for (Map<String, Object> item : trendData) {
            String date = (String) item.get("date");
            dates.add(date != null && date.length() >= 5 ? date.substring(5) : "");
            heartRateData.add(MapValueUtil.getInt(item, "heartRate"));
            bloodOxygenData.add(MapValueUtil.getInt(item, "bloodOxygen"));
            sleepData.add(MapValueUtil.getInt(item, "sleep"));
            temperatureData.add(MapValueUtil.getInt(item, "temperature"));
            pressureData.add(MapValueUtil.getInt(item, "pressure"));
        }

        return new RiskWarningTrendView(
                dates,
                new RiskWarningTrendSeriesView(
                        heartRateData,
                        bloodOxygenData,
                        sleepData,
                        temperatureData,
                        pressureData
                )
        );
    }

    public List<RiskWarningDeptStatView> getDeptWarningStats(String startDate, String endDate) {
        List<RiskWarningDeptStatView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(riskWarningMapper.getDeptWarningStats(startDate, endDate))) {
            result.add(new RiskWarningDeptStatView(
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "heartRate"),
                    MapValueUtil.getInt(row, "bloodOxygen"),
                    MapValueUtil.getInt(row, "sleep"),
                    MapValueUtil.getInt(row, "temperature"),
                    MapValueUtil.getInt(row, "pressure"),
                    MapValueUtil.getInt(row, "total")
            ));
        }
        return result;
    }

    public List<RiskWarningTypeCountView> getTypeDistribution() {
        List<RiskWarningTypeCountView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(riskWarningMapper.getTypeDistribution())) {
            result.add(new RiskWarningTypeCountView(
                    stringValue(row.get("type")),
                    MapValueUtil.getInt(row, "count")
            ));
        }
        return result;
    }

    // ─── 写入：新预警记录 ─────────────────────────────────────────────

    /**
     * 检查指定用户在最近 minutes 分钟内是否有同指标的未处理预警。
     * 用于去重，避免同一用户持续异常时每5秒插入一条相同预警。
     * 查询失败时返回 false（fail-open：宁可多插也不丢失告警）。
     */
    public boolean hasRecentWarning(String userCode, String indicatorName, int minutes) {
        try {
            boolean hit = riskWarningMapper.countRecentWarning(userCode, indicatorName, minutes) > 0;
            healthMetricsService.recordWarningDedup(indicatorName, hit ? "hit" : "miss");
            return hit;
        } catch (Exception e) {
            healthMetricsService.recordWarningDedup(indicatorName, "failed-open");
            log.warn("检查近期预警失败，允许插入: userCode={}, indicator={}, error={}",
                    userCode, indicatorName, e.getMessage());
            return false;
        }
    }

    /**
     * 向当前月份表插入一条新预警记录。
     * 由 DataProcessService 或其他业务逻辑触发时调用。
     */
    public boolean insertWarning(String userCode, String warningType,
                                 String indicatorName, String indicatorValue, String warningLevel) {
        return insertWarning(userCode, warningType, indicatorName, indicatorValue, warningLevel,
                "HEALTH_THRESHOLD", eventCodeForIndicator(indicatorName), null, null);
    }

    public boolean insertWarning(String userCode, String warningType,
                                 String indicatorName, String indicatorValue, String warningLevel,
                                 String eventSource, String eventCode, String deviceImei,
                                 String thresholdSnapshot) {
        String tableName = TableNameUtil.warningRecordTable();
        String source = HealthDataSourceContext.get() == null ? "unknown" : HealthDataSourceContext.get().key();
        try {
            int rows = riskWarningMapper.insertToWarningTable(
                    tableName, userCode, warningType, indicatorName, indicatorValue, warningLevel,
                    eventSource, eventCode, deviceImei, thresholdSnapshot);
            boolean success = rows > 0;
            healthMetricsService.recordWarningGenerated(source, warningType, warningLevel, success ? "success" : "empty");
            return success;
        } catch (Exception e) {
            healthMetricsService.recordWarningGenerated(source, warningType, warningLevel, "failure");
            log.error("[分表] 插入预警到 {} 失败: {}", tableName, e.getMessage(), e);
            return false;
        }
    }

    // ─── 更新：处理预警（需按月份路由） ──────────────────────────────

    /**
     * 处理单条预警。
     *
     * 路由逻辑：
     *   1. 若前端传入 createTime，直接计算对应月份表（避免视图ID重复导致路由错误）
     *   2. 否则从 v_warning_record 视图查出该记录的 create_time 再计算
     *   3. 若目标月份表更新0行，逐一尝试其他月份表（应对ID跨表重复情况）
     *   4. 最后回退更新原始表
     */
    public boolean handleWarning(Long id, String handleBy, String handleRemark, String createTimeStr) {
        // 优先用前端传入的 createTime 直接路由，避免视图中 ID 重复导致路由到错误表
        String primaryTable = null;
        if (createTimeStr != null && !createTimeStr.isEmpty()) {
            LocalDateTime ct = parseDateTime(createTimeStr);
            if (ct != null) primaryTable = TableNameUtil.warningRecordTable(ct);
        }
        if (primaryTable == null) {
            primaryTable = resolveWarningTable(id);
        }

        // 尝试主路由表
        int result = 0;
        if (primaryTable != null) {
            result = riskWarningMapper.handleWarningInTable(primaryTable, id, handleBy, handleRemark);
        }

        // 若未命中，遍历近13个月表（应对 ID 在多表重复的边界情况）
        if (result == 0) {
            LocalDateTime now = LocalDateTime.now();
            java.util.List<String> allTables = TableNameUtil.warningRecordTables(now.minusMonths(12), now);
            for (String t : allTables) {
                if (t.equals(primaryTable)) continue;
                try {
                    result = riskWarningMapper.handleWarningInTable(t, id, handleBy, handleRemark);
                    if (result > 0) break;
                } catch (Exception e) {
                    log.debug("[分表] handleWarning 尝试表 {} 失败(可能不存在): {}", t, e.getMessage());
                }
            }
        }

        // 最终回退：原始表（历史数据）
        if (result == 0) {
            result = riskWarningMapper.handleWarningOriginal(id, handleBy, handleRemark);
        }
        if (result == 0) {
            log.warn("[分表] handleWarning: id={} 在所有表中均未找到可更新记录", id);
        }
        return result > 0;
    }

    /**
     * 批量处理预警。
     *
     * 路由逻辑：
     *   1. 从视图查出所有 id 对应的 create_time
     *   2. 按月份表分组
     *   3. 对每个月份表执行一次批量 UPDATE
     */
    public boolean handleBatch(List<RiskWarningLocatorRequest> locators, String handleBy) {
        if (locators == null || locators.isEmpty()) return false;

        for (RiskWarningLocatorRequest locator : locators) {
            if (locator == null || locator.warningId() == null
                    || locator.occurredAt() == null || locator.occurredAt().isBlank()) {
                throw new IllegalArgumentException("批量处理必须提供 warningId 和 occurredAt");
            }
            if (getWarningDetail(locator.warningId(), locator.occurredAt()) == null) {
                throw new IllegalArgumentException("预警不存在或定位时间不匹配");
            }
        }

        int updated = 0;
        for (RiskWarningLocatorRequest locator : locators) {
            if (handleWarning(locator.warningId(), handleBy, "批量处理", locator.occurredAt())) {
                updated++;
            }
        }
        return updated == locators.size();
    }

    // ─── 私有工具 ────────────────────────────────────────────────────

    /**
     * 根据 id 从视图查出 create_time，并计算对应月份表名。
     * 找不到时返回 null。
     */
    private String resolveWarningTable(Long id) {
        Map<String, Object> info = riskWarningMapper.selectCreateTimeById(id);
        if (info == null) return null;
        LocalDateTime createTime = parseDateTime(info.get("create_time"));
        if (createTime == null) return null;
        return TableNameUtil.warningRecordTable(createTime);
    }

    private List<RiskWarningItemView> toItemViews(List<Map<String, Object>> rows) {
        List<RiskWarningItemView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new RiskWarningItemView(
                    MapValueUtil.toLong(row.get("id")),
                    stringValue(row.get("userName")),
                    stringValue(row.get("userCode")),
                    stringValue(row.get("deptName")),
                    nullableInt(row.get("gender")),
                    nullableInt(row.get("age")),
                    stringValue(row.get("warningType")),
                    stringValue(row.get("warningLevel")),
                    stringValue(row.get("warningValue")),
                    stringValue(row.get("indicatorName")),
                    stringValue(row.get("eventSource")),
                    stringValue(row.get("eventCode")),
                    stringValue(row.get("deviceImei")),
                    stringValue(row.get("thresholdSnapshot")),
                    nullableBoolean(row.get("handled")),
                    stringValue(row.get("createTime")),
                    stringValue(row.get("handleBy")),
                    stringValue(row.get("handleTime")),
                    stringValue(row.get("handleNote"))
            ));
        }
        return result;
    }

    /** 将数据库返回的时间对象（可能是 Timestamp/String）解析为 LocalDateTime */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value, DT_FMT);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Integer nullableInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean nullableBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String eventCodeForIndicator(String indicatorName) {
        if (indicatorName == null) return "HEALTH_UNKNOWN";
        return switch (indicatorName) {
            case "心率" -> "HEART_RATE";
            case "血氧" -> "BLOOD_OXYGEN";
            case "体温" -> "TEMPERATURE";
            case "收缩压" -> "SYSTOLIC_PRESSURE";
            case "压力指数" -> "PRESSURE_INDEX";
            default -> "HEALTH_UNKNOWN";
        };
    }

    private int calculateHandledRate(Map<String, Object> stats) {
        long total = MapValueUtil.getLong(stats, "totalWarnings");
        long handled = MapValueUtil.getLong(stats, "handledWarnings");
        return total > 0 ? (int) (handled * 100 / total) : 0;
    }

}
