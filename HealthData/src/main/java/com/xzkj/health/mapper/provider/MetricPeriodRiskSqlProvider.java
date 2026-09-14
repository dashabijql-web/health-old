package com.xzkj.health.mapper.provider;

import java.util.Map;

public final class MetricPeriodRiskSqlProvider {

    private MetricPeriodRiskSqlProvider() {}

    public static String summary(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return "WITH scoped AS (" + scoped(metric) + "), " +
                "per_user AS (SELECT user_code, MAX(risk_code) AS risk_code, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS abnormal_records, " +
                "COUNT(*) AS total_records FROM scoped GROUP BY user_code) " +
                "SELECT COUNT(*) AS coveredUsers, " +
                "COALESCE(SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END), 0) AS abnormalUsers, " +
                "COALESCE(SUM(CASE WHEN risk_code = 0 THEN 1 ELSE 0 END), 0) AS normalUsers, " +
                "COALESCE(SUM(CASE WHEN risk_code = 1 THEN 1 ELSE 0 END), 0) AS lowUsers, " +
                "COALESCE(SUM(CASE WHEN risk_code = 2 THEN 1 ELSE 0 END), 0) AS warningUsers, " +
                "COALESCE(SUM(CASE WHEN risk_code = 3 THEN 1 ELSE 0 END), 0) AS dangerUsers, " +
                "COALESCE(SUM(abnormal_records), 0) AS abnormalRecords, " +
                "COALESCE(SUM(total_records), 0) AS totalRecords FROM per_user";
    }

    public static String dailyRisk(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return "WITH scoped AS (" + scoped(metric) + "), " +
                "per_user AS (SELECT CAST(record_time AS date) AS date, user_code, " +
                "MAX(risk_code) AS risk_code, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS abnormal_records, " +
                "COUNT(*) AS total_records FROM scoped " +
                "GROUP BY CAST(record_time AS date), user_code) " +
                "SELECT CONVERT(VARCHAR(10), date, 23) AS date, COUNT(*) AS coveredUsers, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS anomalyCount, " +
                "CAST(SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) * 100.0 / " +
                "NULLIF(COUNT(*), 0) AS DECIMAL(6,2)) AS anomalyRate, " +
                "SUM(abnormal_records) AS abnormalRecords, " +
                "SUM(total_records) AS totalRecords FROM per_user " +
                "GROUP BY date ORDER BY date";
    }

    public static String departmentRisk(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return "WITH scoped AS (SELECT hr.user_code, ISNULL(d.dept_name, '未分配') AS dept_name, " +
                metric.riskCode() + " AS risk_code FROM ${tableSource} hr " +
                "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
                "LEFT JOIN department d ON e.dept_id = d.id WHERE " + metric.valid() + dateFilter() + "), " +
                "user_risk AS (SELECT dept_name, user_code, MAX(risk_code) AS risk_code, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS abnormal_records, " +
                "SUM(CASE WHEN risk_code = 3 THEN 1 ELSE 0 END) AS danger_records " +
                "FROM scoped GROUP BY dept_name, user_code) " +
                "SELECT dept_name AS deptName, COUNT(*) AS coveredUsers, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS abnormalUsers, " +
                "SUM(abnormal_records) AS abnormalRecords, " +
                "SUM(danger_records) AS dangerRecords FROM user_risk " +
                "GROUP BY dept_name " +
                "ORDER BY abnormalUsers DESC, abnormalRecords DESC";
    }

    public static String periodUsers(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return userCte(metric) + " SELECT p.user_code AS userCode, ISNULL(e.emp_name, p.user_code) AS userName, " +
                "ISNULL(d.dept_name, '') AS deptName, p.sampleCount, p.abnormalCount, p.anomalyDays, " +
                "p.primaryMin, p.primaryMax, p.secondaryMin, p.secondaryMax, " +
                "p.maxRiskCode AS riskCode, " +
                "CONVERT(VARCHAR(19), p.lastSampleTime, 120) AS lastSampleTime, " +
                "CONVERT(VARCHAR(19), p.lastRecordTime, 120) AS lastRecordTime " +
                "FROM per_user p LEFT JOIN employee e ON p.user_code = e.emp_code " +
                "LEFT JOIN department d ON e.dept_id = d.id " +
                "WHERE ((#{riskCode} >= 0 AND p.maxRiskCode = #{riskCode}) OR " +
                "(#{riskCode} < 0 AND (#{mode} <> 'abnormal' OR p.abnormalCount > 0))) " +
                "ORDER BY CASE WHEN #{riskCode} = 0 OR (#{riskCode} < 0 AND #{mode} = 'covered') " +
                "THEN p.sampleCount ELSE p.abnormalCount END DESC, p.lastSampleTime DESC " +
                "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY";
    }

    public static String countPeriodUsers(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return userCte(metric) + " SELECT COUNT(*) FROM per_user " +
                "WHERE ((#{riskCode} >= 0 AND maxRiskCode = #{riskCode}) OR " +
                "(#{riskCode} < 0 AND (#{mode} <> 'abnormal' OR abnormalCount > 0)))";
    }

    public static String departmentUsers(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return userCte(metric) + " SELECT p.user_code AS userCode, ISNULL(e.emp_name, p.user_code) AS userName, " +
                "ISNULL(d.dept_name, '') AS deptName, p.sampleCount, p.abnormalCount, p.anomalyDays, " +
                "p.primaryMin, p.primaryMax, p.secondaryMin, p.secondaryMax, " +
                "CONVERT(VARCHAR(19), p.lastRecordTime, 120) AS lastRecordTime " +
                "FROM per_user p LEFT JOIN employee e ON p.user_code = e.emp_code " +
                "LEFT JOIN department d ON e.dept_id = d.id " +
                "WHERE ISNULL(d.dept_name, '未分配') = #{dept} AND p.abnormalCount > 0 " +
                "ORDER BY p.abnormalCount DESC, p.lastRecordTime DESC " +
                "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY";
    }

    public static String countDepartmentUsers(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return userCte(metric) + " SELECT COUNT(*) FROM per_user p " +
                "LEFT JOIN employee e ON p.user_code = e.emp_code " +
                "LEFT JOIN department d ON e.dept_id = d.id " +
                "WHERE ISNULL(d.dept_name, '未分配') = #{dept} AND p.abnormalCount > 0";
    }

    public static String userAbnormalRecords(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return "SELECT CONVERT(VARCHAR(19), hr.record_time, 120) AS recordTime, " +
                metric.primary() + " AS primaryValue, " + metric.secondary() + " AS secondaryValue, " +
                metric.direction() + " AS direction, " + metric.level() + " AS level " +
                "FROM ${tableSource} hr WHERE " + metric.valid() + dateFilter() +
                " AND hr.user_code = #{userCode} AND (" + metric.riskCode() + ") > 0 " +
                "ORDER BY hr.record_time DESC OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY";
    }

    public static String countUserAbnormalRecords(Map<String, Object> params) {
        MetricSql metric = metric(params);
        return "SELECT COUNT(*) FROM ${tableSource} hr WHERE " + metric.valid() + dateFilter() +
                " AND hr.user_code = #{userCode} AND (" + metric.riskCode() + ") > 0";
    }

    private static String scoped(MetricSql metric) {
        return "SELECT hr.user_code, hr.record_time, " + metric.primary() + " AS primary_value, " +
                metric.secondary() + " AS secondary_value, " + metric.riskCode() + " AS risk_code " +
                "FROM ${tableSource} hr WHERE " + metric.valid() + dateFilter();
    }

    private static String userCte(MetricSql metric) {
        return "WITH scoped AS (" + scoped(metric) + "), " +
                "per_day AS (SELECT user_code, CAST(record_time AS date) AS d, " +
                "COUNT(*) AS sampleCount, " +
                "SUM(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END) AS abnormalCount, " +
                "MIN(primary_value) AS primaryMin, MAX(primary_value) AS primaryMax, " +
                "MIN(secondary_value) AS secondaryMin, MAX(secondary_value) AS secondaryMax, " +
                "MAX(risk_code) AS maxRiskCode, MAX(record_time) AS lastSampleTime, " +
                "MAX(CASE WHEN risk_code > 0 THEN record_time END) AS lastRecordTime " +
                "FROM scoped GROUP BY user_code, CAST(record_time AS date)), " +
                "per_user AS (SELECT user_code, SUM(sampleCount) AS sampleCount, " +
                "SUM(abnormalCount) AS abnormalCount, " +
                "SUM(CASE WHEN abnormalCount > 0 THEN 1 ELSE 0 END) AS anomalyDays, " +
                "MIN(primaryMin) AS primaryMin, MAX(primaryMax) AS primaryMax, " +
                "MIN(secondaryMin) AS secondaryMin, MAX(secondaryMax) AS secondaryMax, " +
                "MAX(maxRiskCode) AS maxRiskCode, MAX(lastSampleTime) AS lastSampleTime, " +
                "MAX(lastRecordTime) AS lastRecordTime FROM per_day GROUP BY user_code) ";
    }

    private static String dateFilter() {
        return " AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
                "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate}))";
    }

    private static MetricSql metric(Map<String, Object> params) {
        return switch (String.valueOf(params.get("metric"))) {
            case "pressure" -> new MetricSql(
                    "hr.pressure IS NOT NULL AND hr.pressure > 0",
                    "hr.pressure", "CAST(NULL AS INT)",
                    "CASE WHEN hr.pressure >= 85 THEN 3 WHEN hr.pressure >= 70 THEN 2 ELSE 0 END",
                    "'high'",
                    "CASE WHEN hr.pressure >= 85 THEN 'danger' ELSE 'warning' END");
            case "bloodPressure" -> new MetricSql(
                    "hr.blood_pressure_high IS NOT NULL AND hr.blood_pressure_high > 0 " +
                            "AND hr.blood_pressure_low IS NOT NULL AND hr.blood_pressure_low > 0",
                    "hr.blood_pressure_high", "hr.blood_pressure_low",
                    "CASE WHEN hr.blood_pressure_high >= 160 OR hr.blood_pressure_low >= 100 THEN 3 " +
                            "WHEN hr.blood_pressure_high >= 120 OR hr.blood_pressure_low >= 80 THEN 2 " +
                            "WHEN hr.blood_pressure_high < 90 OR hr.blood_pressure_low < 60 THEN 1 ELSE 0 END",
                    "CASE WHEN hr.blood_pressure_high < 90 OR hr.blood_pressure_low < 60 THEN 'low' ELSE 'high' END",
                    "CASE WHEN hr.blood_pressure_high >= 160 OR hr.blood_pressure_low >= 100 THEN 'danger' ELSE 'warning' END");
            case "bloodOxygen" -> new MetricSql(
                    "hr.blood_oxygen IS NOT NULL AND hr.blood_oxygen > 0 AND hr.blood_oxygen <= 100",
                    "hr.blood_oxygen", "CAST(NULL AS INT)",
                    "CASE WHEN hr.blood_oxygen < 90 THEN 3 WHEN hr.blood_oxygen < 95 THEN 2 ELSE 0 END",
                    "'low'",
                    "CASE WHEN hr.blood_oxygen < 90 THEN 'danger' ELSE 'warning' END");
            default -> throw new IllegalArgumentException("不支持的指标类型");
        };
    }

    private record MetricSql(
            String valid,
            String primary,
            String secondary,
            String riskCode,
            String direction,
            String level
    ) {}
}
