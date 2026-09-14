package com.xzkj.health.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 指标每日检测/异常人数动态 SQL 提供器
 */
public class MetricDailySqlProvider {

    private static final Map<String, String[]> METRIC_MAP = Map.of(
        "heartRate",   new String[]{"heart_rate",   "heart_rate > 100 OR heart_rate < 60"},
        "bloodOxygen", new String[]{"blood_oxygen", "blood_oxygen < 95"},
        "steps",       new String[]{"steps",        "steps < 5000"},
        "temperature", new String[]{"temperature",  "temperature > 37.5 OR temperature < 36"},
        "pressure",    new String[]{"pressure",     "pressure > 60"}
    );

    /** 当前月份分区表名，如 health_record_202603 */
    private static String currentMonthTable() {
        return "health_record_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 班前健康达标率 — 直接查当月分区表，避免 v_health_record UNION ALL 13 张表的开销
     */
    public String getTodayPreShiftCompliance(Map<String, Object> params) {
        String table = currentMonthTable();
        // Optimized: use MAX(record_time) GROUP BY user_code to find latest record per user,
        // then join back to get metrics — avoids ROW_NUMBER() window function over 500K+ rows.
        return "SELECT " +
               "  COUNT(*) AS totalToday, " +
               "  SUM(CASE WHEN " +
               "    (lr.heart_rate IS NULL OR (lr.heart_rate >= 60 AND lr.heart_rate <= 100)) " +
               "    AND (lr.blood_oxygen IS NULL OR lr.blood_oxygen >= 95) " +
               "    AND (lr.blood_pressure_high IS NULL OR lr.blood_pressure_high < 140) " +
               "    AND (lr.blood_pressure_low IS NULL OR lr.blood_pressure_low < 90) " +
               "    AND (lr.temperature IS NULL OR (lr.temperature >= 36.0 AND lr.temperature <= 37.5)) " +
               "    THEN 1 ELSE 0 END) AS qualifiedCount, " +
               "  SUM(CASE WHEN " +
               "    (lr.heart_rate IS NOT NULL AND (lr.heart_rate < 60 OR lr.heart_rate > 100)) " +
               "    OR (lr.blood_oxygen IS NOT NULL AND lr.blood_oxygen < 95) " +
               "    OR (lr.blood_pressure_high IS NOT NULL AND lr.blood_pressure_high >= 140) " +
               "    OR (lr.blood_pressure_low IS NOT NULL AND lr.blood_pressure_low >= 90) " +
               "    OR (lr.temperature IS NOT NULL AND (lr.temperature < 36.0 OR lr.temperature > 37.5)) " +
               "    THEN 1 ELSE 0 END) AS failedCount " +
               "FROM " + table + " lr " +
               "INNER JOIN ( " +
               "  SELECT user_code, MAX(record_time) AS max_time " +
               "  FROM " + table + " " +
               "  WHERE record_time >= CONVERT(date, GETDATE()) " +
               "  GROUP BY user_code " +
               ") AS latest ON lr.user_code = latest.user_code AND lr.record_time = latest.max_time";
    }

    /**
     * 入井准入名单 — 直接查当月分区表
     */
    public String getTodayMineEntryList(Map<String, Object> params) {
        String table = currentMonthTable();
        int size = params.containsKey("size") ? ((Number) params.get("size")).intValue() : 200;
        return "SELECT TOP " + size + " " +
               "  ISNULL(e.emp_name, lr.user_code) AS empName, " +
               "  ISNULL(e.emp_code, lr.user_code) AS empCode, " +
               "  ISNULL(d.dept_name, '') AS deptName, " +
               "  ISNULL(jt.type_name, '') AS jobTypeName," +
               "  lr.heart_rate AS heartRate, " +
               "  lr.blood_oxygen AS bloodOxygen, " +
               "  lr.blood_pressure_high AS systolic, " +
               "  lr.blood_pressure_low AS diastolic, " +
               "  lr.temperature AS temperature, " +
               "  lr.record_time AS recordTime, " +
               "  CASE WHEN " +
               "    (lr.heart_rate IS NULL OR (lr.heart_rate >= 60 AND lr.heart_rate <= 100)) " +
               "    AND (lr.blood_oxygen IS NULL OR lr.blood_oxygen >= 95) " +
               "    AND (lr.blood_pressure_high IS NULL OR lr.blood_pressure_high < 140) " +
               "    AND (lr.blood_pressure_low IS NULL OR lr.blood_pressure_low < 90) " +
               "    AND (lr.temperature IS NULL OR (lr.temperature >= 36.0 AND lr.temperature <= 37.5)) " +
               "    THEN 1 ELSE 0 END AS qualified " +
               "FROM " + table + " lr " +
               "INNER JOIN ( " +
               "  SELECT user_code, MAX(record_time) AS max_time " +
               "  FROM " + table + " " +
               "  WHERE record_time >= CONVERT(date, GETDATE()) " +
               "  GROUP BY user_code " +
               ") AS latest ON lr.user_code = latest.user_code AND lr.record_time = latest.max_time " +
               "LEFT JOIN employee e ON lr.user_code = e.emp_code " +
               "LEFT JOIN department d ON e.dept_id = d.id " +
               "LEFT JOIN job_type jt ON e.job_type_id = jt.id " +
               "ORDER BY qualified ASC, lr.record_time DESC";
    }

    public String getMetricDailyDetail(Map<String, Object> params) {
        String metricType = (String) params.get("metricType");
        String[] meta = METRIC_MAP.get(metricType);
        if (meta == null) throw new IllegalArgumentException("Unknown metricType: " + metricType);
        String col = meta[0];
        String anomaly = meta[1];
        // 优先使用 tableSource（分区表），兜底使用 v_health_record
        String tableSource = params.containsKey("tableSource") && params.get("tableSource") != null
                ? (String) params.get("tableSource")
                : "v_health_record";
        return "SELECT day, personCount, abnormalPersonCount " +
               "FROM ( " +
               "  SELECT CONVERT(VARCHAR(10), record_time, 120) AS day, " +
               "  COUNT(DISTINCT user_code) AS personCount, " +
               "  COUNT(DISTINCT CASE WHEN " + anomaly + " THEN user_code END) AS abnormalPersonCount " +
               "  FROM " + tableSource + " AS src " +
               "  WHERE " + col + " IS NOT NULL " +
               "  AND record_time >= CONVERT(date, '" + params.get("startTime") + "') " +
               "  AND record_time <  DATEADD(DAY, 1, CONVERT(date, '" + params.get("endTime") + "')) " +
               "  GROUP BY CONVERT(VARCHAR(10), record_time, 120) " +
               ") AS daily ORDER BY day";
    }
}
