package com.xzkj.health.mapper.provider;

import com.xzkj.health.util.TableSourceUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;

/** Builds warning queries against only the monthly tables covered by a time window. */
public final class WarningTimeWindowSqlProvider {

    private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();
    private static final String COLUMNS = "id, user_code, warning_type, indicator_name, indicator_value, "
            + "warning_level, is_handled, handle_time, handle_by, remark, create_time, event_source, "
            + "event_code, device_imei, threshold_snapshot";

    private WarningTimeWindowSqlProvider() {
    }

    public static String list(Map<String, Object> params) {
        String source = source(params);
        return "SELECT "
                + "wr.id, "
                + "ISNULL(e.emp_name, wr.user_code) AS userName, "
                + "wr.user_code AS userCode, "
                + "ISNULL(d.dept_name, '未知部门') AS deptName, "
                + "e.gender AS gender, "
                + "CASE WHEN e.birth_date IS NOT NULL THEN DATEDIFF(YEAR, e.birth_date, GETDATE()) ELSE NULL END AS age, "
                + "wr.warning_type AS warningType, "
                + "wr.warning_level AS warningLevel, "
                + "wr.indicator_value AS warningValue, "
                + "wr.indicator_name AS indicatorName, "
                + "COALESCE(wr.event_source, CASE WHEN wr.indicator_name = N'行为报警' OR wr.warning_type LIKE N'%报警%' "
                + "THEN 'DEVICE_ALARM' ELSE 'HEALTH_THRESHOLD' END) AS eventSource, "
                + "COALESCE(wr.event_code, CASE WHEN wr.warning_type LIKE N'%SOS%' THEN 'SOS' "
                + "WHEN wr.warning_type LIKE N'%跌倒%' THEN 'FALL' WHEN wr.warning_type LIKE N'%房颤%' THEN 'AFIB' "
                + "WHEN wr.warning_type LIKE N'%拆卸%' THEN 'TAMPER' WHEN wr.warning_type LIKE N'%红外%' THEN 'INFRARED' "
                + "WHEN wr.warning_type LIKE N'%心率%' THEN 'HEART_RATE' WHEN wr.warning_type LIKE N'%血氧%' THEN 'BLOOD_OXYGEN' "
                + "WHEN wr.warning_type LIKE N'%体温%' THEN 'TEMPERATURE' WHEN wr.warning_type LIKE N'%血压%' THEN 'SYSTOLIC_PRESSURE' "
                + "WHEN wr.warning_type LIKE N'%压力%' THEN 'PRESSURE_INDEX' ELSE 'LEGACY_UNKNOWN' END) AS eventCode, "
                + "wr.device_imei AS deviceImei, wr.threshold_snapshot AS thresholdSnapshot, "
                + "wr.is_handled AS handled, wr.create_time AS createTime, wr.handle_by AS handleBy, "
                + "wr.handle_time AS handleTime, wr.remark AS handleNote "
                + "FROM " + source + " wr "
                + "LEFT JOIN employee e ON wr.user_code = e.emp_code "
                + "LEFT JOIN department d ON e.dept_id = d.id "
                + "WHERE wr.create_time >= CONVERT(DATETIME, #{startAt}) "
                + "AND wr.create_time < CONVERT(DATETIME, #{endAt}) "
                + levelAndHandled(params)
                + " ORDER BY wr.create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY";
    }

    public static String count(Map<String, Object> params) {
        return "SELECT COUNT(*) FROM " + source(params) + " wr "
                + "WHERE wr.create_time >= CONVERT(DATETIME, #{startAt}) "
                + "AND wr.create_time < CONVERT(DATETIME, #{endAt})"
                + levelAndHandled(params);
    }

    private static String source(Map<String, Object> params) {
        LocalDateTime start = parseDateTime(params.get("startAt"));
        LocalDateTime end = parseDateTime(params.get("endAt"));
        return TableSourceUtil.warningRecordSource(start, end, COLUMNS);
    }

    private static LocalDateTime parseDateTime(Object value) {
        String text = String.valueOf(value).trim().replace('T', ' ');
        return LocalDateTime.parse(text, DATE_TIME);
    }

    private static String levelAndHandled(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        if (params.get("level") != null && !String.valueOf(params.get("level")).isBlank()) {
            sql.append(" AND wr.warning_level = #{level}");
        }
        if (params.get("handled") != null) {
            sql.append(" AND wr.is_handled = #{handled}");
        }
        return sql.toString();
    }
}
