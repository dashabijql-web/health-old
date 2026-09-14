package com.xzkj.health.mapper;

import com.xzkj.health.dto.realtime.RealtimeAlertRow;
import com.xzkj.health.dto.realtime.RealtimeOverviewRow;
import com.xzkj.health.dto.realtime.RealtimeStatisticsRow;
import com.xzkj.health.dto.realtime.RealtimeUserDetailRow;
import com.xzkj.health.dto.realtime.RealtimeUserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * 实时监控Mapper
 * 概览与历史统计保留近7天口径；实时人员列表使用可配置的短时上报窗口。
 */
@Mapper
public interface RealtimeMapper {

    /**
     * 获取近7天平均健康数据概览
     */
    @Select("SELECT " +
            "ISNULL(AVG(CAST(heart_rate AS FLOAT)), 0) AS avgHeartRate, " +
            "ISNULL(AVG(CAST(blood_oxygen AS FLOAT)), 0) AS avgBloodOxygen, " +
            "ISNULL(AVG(CAST(steps AS FLOAT)), 0) AS avgSteps, " +
            "ISNULL(AVG(CAST(calories AS FLOAT)), 0) AS avgCalories, " +
            "ISNULL(AVG(CAST(temperature AS FLOAT)) / 10.0, 0) AS avgTemperature, " +
            "ISNULL(AVG(CAST(sleep_minutes AS FLOAT)) / 60.0, 0) AS avgSleep, " +
            "ISNULL(AVG(CAST(blood_pressure_high AS FLOAT)), 0) AS avgBloodPressureHigh, " +
            "ISNULL(AVG(CAST(blood_pressure_low AS FLOAT)), 0) AS avgBloodPressureLow, " +
            "ISNULL(AVG(CAST(pressure AS FLOAT)), 0) AS avgPressure, " +
            "(SELECT COUNT(*) FROM v_warning_record WHERE create_time >= DATEADD(HOUR, -168, GETDATE()) AND is_handled = 0) AS todayWarningCount " +
            "FROM v_health_record " +
            "WHERE record_time >= DATEADD(HOUR, -168, GETDATE())")
    RealtimeOverviewRow getTodayAvgData();

    /**
     * 获取短时活跃人员快照。按人取窗口内最大 id 再回表主键，避免扫全窗口排序。
     */
    @SelectProvider(type = RealtimeOnlineSqlProvider.class, method = "getActiveUsers")
    List<RealtimeUserRow> getActiveUsersDirect(@Param("tableSource") String tableSource,
                                               @Param("onlineWindowMinutes") int onlineWindowMinutes);

    /** 在线人员身份目录，不含月表流水，供 Redis 最新体征快照关联姓名部门。 */
    @Select("SELECT e.id, e.emp_code AS userCode, e.emp_name AS userName, e.gender, " +
            "CASE WHEN e.birth_date IS NOT NULL THEN FLOOR(DATEDIFF(day, e.birth_date, GETDATE()) / 365.25) ELSE NULL END AS age, " +
            "d.dept_name AS deptName, j.risk_level AS riskLevel, dv.imei AS imei " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "LEFT JOIN job_type j ON e.job_type_id = j.id " +
            "LEFT JOIN device_user du ON du.emp_id = e.id AND du.unbind_time IS NULL " +
            "LEFT JOIN device dv ON dv.id = du.device_id " +
            "WHERE (e.status IS NULL OR e.status = 0)")
    List<RealtimeUserRow> getOnlineEmployeeDirectory();

    class RealtimeOnlineSqlProvider {
        public String getActiveUsers(Map<String, Object> params) {
            String source = String.valueOf(params.get("tableSource"));
            if (!source.matches("health_record_\\d{6}|\\(SELECT [a-zA-Z0-9_, ]+ FROM health_record_\\d{6} UNION ALL SELECT [a-zA-Z0-9_, ]+ FROM health_record_\\d{6}\\)")) {
                throw new IllegalArgumentException("Invalid realtime table source");
            }

            boolean union = source.startsWith("(");
            String hinted = union
                    ? source.replaceAll("(health_record_\\d{6})", "$1 WITH (NOLOCK)")
                    : source;
            String fromR = union ? hinted + " AS r" : hinted + " AS r WITH (NOLOCK)";
            String fromT = union ? hinted + " AS t" : hinted + " AS t WITH (NOLOCK)";
            String latest = "(SELECT r.user_code, MAX(r.id) AS max_id FROM " + fromR +
                    " WHERE r.record_time >= DATEADD(MINUTE, -#{onlineWindowMinutes}, GETDATE()) GROUP BY r.user_code)";

            return "SELECT e.id, e.emp_code AS userCode, e.emp_name AS userName, e.gender, " +
                    "CASE WHEN e.birth_date IS NOT NULL THEN FLOOR(DATEDIFF(day, e.birth_date, GETDATE()) / 365.25) ELSE NULL END AS age, " +
                    "d.dept_name AS deptName, j.risk_level AS riskLevel, " +
                    "t.heart_rate AS heartRate, t.blood_oxygen AS bloodOxygen, t.steps, t.calories, " +
                    "t.temperature / 10.0 AS temperature, t.sleep_minutes / 60.0 AS sleepHours, " +
                    "t.blood_pressure_high AS bloodPressureHigh, t.blood_pressure_low AS bloodPressureLow, t.pressure, " +
                    "CONVERT(varchar(23), CASE WHEN t.heart_rate IS NOT NULL OR t.blood_oxygen IS NOT NULL OR t.temperature IS NOT NULL " +
                    "OR t.blood_pressure_high IS NOT NULL OR t.blood_pressure_low IS NOT NULL OR t.pressure IS NOT NULL " +
                    "THEN t.record_time END, 121) AS lastUpdate, " +
                    "DATEDIFF(SECOND, CASE WHEN t.heart_rate IS NOT NULL OR t.blood_oxygen IS NOT NULL OR t.temperature IS NOT NULL " +
                    "OR t.blood_pressure_high IS NOT NULL OR t.blood_pressure_low IS NOT NULL OR t.pressure IS NOT NULL " +
                    "THEN t.record_time END, GETDATE()) AS dataAgeSeconds, dv.imei AS imei " +
                    "FROM " + fromT + " " +
                    "INNER JOIN " + latest + " latest ON t.id = latest.max_id " +
                    "INNER JOIN employee e ON e.emp_code = t.user_code " +
                    "LEFT JOIN department d ON e.dept_id = d.id " +
                    "LEFT JOIN job_type j ON e.job_type_id = j.id " +
                    "LEFT JOIN device_user du ON du.emp_id = e.id AND du.unbind_time IS NULL " +
                    "LEFT JOIN device dv ON dv.id = du.device_id " +
                    "WHERE (e.status IS NULL OR e.status = 0) ORDER BY t.record_time DESC";
        }
    }

    /**
     * 获取实时统计数据（近7天口径）— 直接查分区表版本
     * 168h 窗口最多跨当月+上月两张表，避免扫 v_health_record UNION ALL 13 张表
     */
    @SelectProvider(type = RealtimeStatsSqlProvider.class, method = "getStatisticsDirect")
    RealtimeStatisticsRow getStatisticsDirect();

    /**
     * 动态 SQL 提供器：只 UNION 当月+上月两张分区表，而非 v_health_record 全部13张
     */
    class RealtimeStatsSqlProvider {
        public String getStatisticsDirect() {
            // 统一管控/指挥中心只要在册人数；在线人数由 Redis 短窗口快照填充。
            // 禁止再扫近 7 天月表，刷新时会把 SQL Server 打满并在 IDEA 控制台刷超时栈。
            return "SELECT CAST(0 AS BIGINT) AS onlineUsers, " +
                   "(SELECT COUNT(*) FROM employee WHERE status IS NULL OR status = 0) AS totalUsers, " +
                   "CAST(0 AS BIGINT) AS weekRecords, CAST(0 AS BIGINT) AS todayRecords, " +
                   "CAST(0 AS FLOAT) AS onlineRate, CAST(0 AS FLOAT) AS normalRate";
        }
    }

    /**
     * 获取实时统计数据（近7天口径）
     * 优化：用 CTE 将 v_health_record 的 168h 窗口扫描从 5 次缩减为 1 次
     */
    @Select("WITH Stats7d AS ( " +
            "  SELECT " +
            "    COUNT(*) AS totalRecords, " +
            "    COUNT(DISTINCT user_code) AS distinctUsers, " +
            "    SUM(CASE WHEN heart_rate >= 60 AND heart_rate <= 100 AND blood_oxygen >= 95 THEN 1 ELSE 0 END) AS healthyRecords " +
            "  FROM v_health_record " +
            "  WHERE record_time >= DATEADD(HOUR, -168, GETDATE()) " +
            "), " +
            "TotalEmp AS ( " +
            "  SELECT COUNT(*) AS cnt FROM employee WHERE status IS NULL OR status = 0 " +
            ") " +
            "SELECT " +
            "  (SELECT distinctUsers  FROM Stats7d) AS onlineUsers, " +
            "  (SELECT cnt            FROM TotalEmp) AS totalUsers, " +
            "  (SELECT totalRecords   FROM Stats7d) AS weekRecords, " +
            "  (SELECT totalRecords   FROM Stats7d) AS todayRecords, " +
            "  CASE WHEN (SELECT cnt FROM TotalEmp) > 0 " +
            "       THEN (SELECT distinctUsers FROM Stats7d) * 100 / (SELECT cnt FROM TotalEmp) " +
            "       ELSE 0 END AS onlineRate, " +
            "  CASE WHEN (SELECT totalRecords FROM Stats7d) > 0 " +
            "       THEN (SELECT healthyRecords FROM Stats7d) * 100 / (SELECT totalRecords FROM Stats7d) " +
            "       ELSE 0 END AS normalRate")
    Map<String, Object> getStatistics();

    /**
     * 获取近期未处理告警列表（安全指挥中心实时告警栏）
     */
    @Select("SELECT TOP (#{limit}) " +
            "w.id, " +
            "w.user_code AS userCode, " +
            "ISNULL(e.emp_name, w.user_code) AS userName, " +
            "d.dept_name AS deptName, " +
            "w.warning_type AS warningType, " +
            "w.indicator_name AS indicatorName, " +
            "w.indicator_value AS indicatorValue, " +
            "w.warning_level AS warningLevel, " +
            "w.is_handled AS handled, " +
            "CONVERT(varchar(19), w.create_time, 120) AS createTime " +
            "FROM v_warning_record w " +
            "LEFT JOIN employee e ON w.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE w.is_handled = 0 " +
            "AND w.create_time >= DATEADD(DAY, -7, GETDATE()) " +
            "ORDER BY w.create_time DESC")
    List<RealtimeAlertRow> getRecentAlerts(@Param("limit") int limit);

    /**
     * 获取用户实时数据
     */
    @Select("SELECT TOP 1 " +
            "hr.user_code AS userCode, " +
            "e.emp_name AS userName, " +
            "d.dept_name AS deptName, " +
            "hr.heart_rate AS heartRate, " +
            "hr.blood_oxygen AS bloodOxygen, " +
            "hr.temperature / 10.0 AS temperature, " +
            "hr.blood_pressure_high AS bloodPressureHigh, " +
            "hr.blood_pressure_low AS bloodPressureLow, " +
            "hr.pressure, " +
            "hr.steps, " +
            "hr.calories, " +
            "hr.sleep_minutes / 60.0 AS sleepHours, " +
            "CONVERT(varchar(19), hr.record_time, 120) AS lastUpdate, " +
            "CASE " +
            "    WHEN hr.heart_rate < 60 OR hr.heart_rate > 100 THEN 'warning' " +
            "    WHEN hr.blood_oxygen < 95 THEN 'warning' " +
            "    WHEN hr.temperature < 360 OR hr.temperature > 375 THEN 'warning' " +
            "    WHEN hr.blood_pressure_high > 139 OR hr.blood_pressure_low > 89 THEN 'warning' " +
            "    WHEN hr.pressure > 84 THEN 'warning' " +
            "    ELSE 'normal' " +
            "END AS status " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON e.emp_code = hr.user_code " +
            "LEFT JOIN department d ON d.id = e.dept_id " +
            "WHERE hr.user_code = #{userCode} " +
            "ORDER BY hr.record_time DESC")
    RealtimeUserDetailRow getUserRealtimeData(@Param("userCode") String userCode);
}
