package com.xzkj.health.mapper;

import com.xzkj.health.dto.dashboard.DashboardBodyAverageRow;
import com.xzkj.health.dto.dashboard.DashboardDailyAnomalyRateRow;
import com.xzkj.health.dto.dashboard.DashboardDeviceStatsRow;
import com.xzkj.health.dto.dashboard.DashboardHourCountRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardTopUserRow;
import com.xzkj.health.dto.dashboard.DashboardWarningEventRow;
import com.xzkj.health.dto.dashboard.DashboardWarningRateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DashboardOverviewMapper {

    @Select("SELECT " +
            "COUNT(CASE WHEN heart_rate   IS NOT NULL THEN 1 END) AS heart_rate, " +
            "COUNT(CASE WHEN blood_oxygen IS NOT NULL THEN 1 END) AS blood_oxygen, " +
            "COUNT(CASE WHEN sleep_minutes IS NOT NULL THEN 1 END) AS sleep, " +
            "COUNT(CASE WHEN steps        IS NOT NULL THEN 1 END) AS steps, " +
            "COUNT(CASE WHEN temperature  IS NOT NULL THEN 1 END) AS temperature, " +
            "COUNT(CASE WHEN pressure     IS NOT NULL THEN 1 END) AS pressure " +
            "FROM ${healthSource} AS hr_src " +
            "WHERE record_time >= CONVERT(date, #{startTime}) " +
            "AND   record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime}))")
    DashboardMetricCountRow getCountsByRangeDirect(@Param("healthSource") String healthSource,
                                                   @Param("startTime")    String startTime,
                                                   @Param("endTime")      String endTime);

    @Select("SELECT " +
            "AVG(CAST(sleep_minutes AS FLOAT)) / 60.0 AS avg_sleep, " +
            "AVG(CAST(pressure     AS FLOAT)) AS avg_pressure, " +
            "AVG(CAST(blood_oxygen AS FLOAT)) AS avg_blood_oxygen, " +
            "AVG(CAST(heart_rate   AS FLOAT)) AS avg_heart_rate, " +
            "AVG(CAST(steps        AS FLOAT)) AS avg_steps, " +
            "AVG(CAST(temperature  AS FLOAT)) / 10.0 AS avg_temperature, " +
            "AVG(CAST(blood_pressure_high AS FLOAT)) AS avg_blood_pressure_high, " +
            "AVG(CAST(blood_pressure_low AS FLOAT)) AS avg_blood_pressure_low, " +
            "AVG(CAST(calories AS FLOAT)) AS avg_calories " +
            "FROM ${healthSource} AS hr_src " +
            "WHERE record_time >= CONVERT(date, #{startTime}) " +
            "AND   record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime}))")
    DashboardBodyAverageRow getAverageByRangeDirect(@Param("healthSource") String healthSource,
                                                    @Param("startTime")    String startTime,
                                                    @Param("endTime")      String endTime);

    @Select("SELECT TOP 15 " +
            "ISNULL(e.emp_name, w.user_code) AS user_name, " +
            "w.user_code AS user_code, " +
            "COUNT(*) AS count " +
            "FROM ${warningSource} AS w " +
            "LEFT JOIN employee e ON w.user_code = e.emp_code " +
            "WHERE w.create_time >= CONVERT(date, #{startTime}) " +
            "AND   w.create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "GROUP BY w.user_code, e.emp_name " +
            "ORDER BY count DESC")
    List<DashboardTopUserRow> getTop5ByRangeDirect(@Param("warningSource") String warningSource,
                                                   @Param("startTime")     String startTime,
                                                   @Param("endTime")       String endTime);

    @Select("SELECT name, " +
            "ISNULL(cnt * 100 / NULLIF((SELECT COUNT(*) FROM employee WHERE status IS NULL OR status = 0), 0), 0) AS rate, " +
            "icon " +
            "FROM ( " +
            "  SELECT '心率预警率' AS name, " +
            "  COUNT(DISTINCT CASE WHEN warning_type LIKE '%心率%' THEN user_code END) AS cnt, " +
            "  'el-icon-heart' AS icon " +
            "  FROM ${warningSource} AS wr_src " +
            "  WHERE create_time >= CONVERT(date, #{startTime}) " +
            "  AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  UNION ALL " +
            "  SELECT '血氧预警率', " +
            "  COUNT(DISTINCT CASE WHEN warning_type LIKE '%血氧%' THEN user_code END), " +
            "  'el-icon-data-analysis' " +
            "  FROM ${warningSource} AS wr_src " +
            "  WHERE create_time >= CONVERT(date, #{startTime}) " +
            "  AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  UNION ALL " +
            "  SELECT '体温预警率', " +
            "  COUNT(DISTINCT CASE WHEN warning_type LIKE '%体温%' THEN user_code END), " +
            "  'el-icon-thermometer' " +
            "  FROM ${warningSource} AS wr_src " +
            "  WHERE create_time >= CONVERT(date, #{startTime}) " +
            "  AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  UNION ALL " +
            "  SELECT '压力预警率', " +
            "  COUNT(DISTINCT CASE WHEN warning_type LIKE '%压力%' THEN user_code END), " +
            "  'el-icon-warning' " +
            "  FROM ${warningSource} AS wr_src " +
            "  WHERE create_time >= CONVERT(date, #{startTime}) " +
            "  AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            ") AS t")
    List<DashboardWarningRateRow> getWarningRatesByRangeDirect(@Param("warningSource") String warningSource,
                                                               @Param("startTime")     String startTime,
                                                               @Param("endTime")       String endTime);

    @Select("SELECT CONVERT(VARCHAR(10), stat_date, 120) AS date, " +
            "heart_rate_rate AS heart_rate_rate, blood_oxygen_rate AS blood_oxygen_rate, " +
            "temperature_rate AS temperature_rate, pressure_rate AS pressure_rate " +
            "FROM health_daily_stats " +
            "WHERE stat_date >= CONVERT(date,#{startDate}) AND stat_date <= CONVERT(date,#{endDate}) " +
            "ORDER BY stat_date ASC")
    List<DashboardDailyAnomalyRateRow> getDailyStatsFromSummary(@Param("startDate") String startDate,
                                                                @Param("endDate")   String endDate);

    @Select("SELECT employees.total, bindings.bound_devices, " +
            "ISNULL(activity.active_count * 100 / NULLIF(employees.total, 0), 0) AS active_rate, " +
            "ISNULL(activity.active_count * 100 / NULLIF(employees.total, 0), 0) AS usage_rate, " +
            "ISNULL(warnings.warning_count * 100 / NULLIF(employees.total, 0), 0) AS warning_rate, " +
            "battery.low_battery " +
            "FROM (SELECT COUNT(*) AS total FROM employee WHERE status IS NULL OR status = 0) employees " +
            "CROSS JOIN (SELECT COUNT(*) AS bound_devices FROM device_user WHERE is_current = 1) bindings " +
            "CROSS JOIN (" +
            "  SELECT COUNT(DISTINCT hr.user_code) AS active_count FROM ${healthSource} AS hr " +
            "  INNER JOIN employee e ON e.emp_code = hr.user_code " +
            "  WHERE (e.status IS NULL OR e.status = 0) " +
            "  AND hr.record_time >= CONVERT(date, #{startTime}) " +
            "  AND hr.record_time < DATEADD(DAY, 1, CONVERT(date, #{endTime}))" +
            ") activity " +
            "CROSS JOIN (" +
            "  SELECT COUNT(DISTINCT wr.user_code) AS warning_count FROM ${warningSource} AS wr " +
            "  INNER JOIN employee e ON e.emp_code = wr.user_code " +
            "  WHERE (e.status IS NULL OR e.status = 0) " +
            "  AND wr.create_time >= CONVERT(date, #{startTime}) " +
            "  AND wr.create_time < DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  AND wr.is_handled = 0" +
            ") warnings " +
            "CROSS JOIN (SELECT COUNT(*) AS low_battery FROM device " +
            "  WHERE battery_level IS NOT NULL AND battery_level > 0 AND battery_level < 20) battery")
    DashboardDeviceStatsRow getDeviceStatsByRangeDirect(@Param("healthSource")  String healthSource,
                                                        @Param("warningSource") String warningSource,
                                                        @Param("startTime")     String startTime,
                                                         @Param("endTime")       String endTime);

    @Select("SELECT employees.total, bindings.bound_devices, " +
            "ISNULL(activity.active_count * 100 / NULLIF(employees.total, 0), 0) AS active_rate, " +
            "ISNULL(activity.active_count * 100 / NULLIF(employees.total, 0), 0) AS usage_rate, " +
            "ISNULL(warnings.warning_count * 100 / NULLIF(employees.total, 0), 0) AS warning_rate, " +
            "battery.low_battery " +
            "FROM (SELECT COUNT(*) AS total FROM employee WHERE status IS NULL OR status = 0) employees " +
            "CROSS JOIN (SELECT COUNT(*) AS bound_devices FROM device_user WHERE is_current = 1) bindings " +
            "CROSS JOIN (SELECT COUNT(DISTINCT user_code) AS active_count FROM health_user_daily_summary " +
            "  WHERE stat_date >= CONVERT(date, #{startTime}) AND stat_date <= CONVERT(date, #{endTime})) activity " +
            "CROSS JOIN (SELECT COUNT(DISTINCT user_code) AS warning_count FROM warning_user_daily_summary " +
            "  WHERE stat_date >= CONVERT(date, #{startTime}) AND stat_date <= CONVERT(date, #{endTime})) warnings " +
            "CROSS JOIN (SELECT COUNT(*) AS low_battery FROM device " +
            "  WHERE battery_level IS NOT NULL AND battery_level > 0 AND battery_level < 20) battery")
    DashboardDeviceStatsRow getDeviceStatsFromDailySummary(@Param("startTime") String startTime,
                                                            @Param("endTime") String endTime);

    @Select("SELECT COUNT(*) FROM dashboard_daily_refresh_state " +
            "WHERE stat_date >= CONVERT(date, #{startTime}) AND stat_date <= CONVERT(date, #{endTime})")
    int countRefreshedSummaryDays(@Param("startTime") String startTime,
                                  @Param("endTime") String endTime);

    @Select("SELECT DATEPART(HOUR, create_time) AS hour_num, COUNT(*) AS count " +
            "FROM v_warning_record " +
            "WHERE create_time >= CONVERT(date, #{date}) " +
            "AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{date})) " +
            "GROUP BY DATEPART(HOUR, create_time) " +
            "ORDER BY hour_num ASC")
    List<DashboardHourCountRow> getWarningCountsByHour(@Param("date") String date);

    @Select("SELECT TOP (#{limit}) " +
            "w.id, " +
            "w.warning_type AS warning_type, " +
            "ISNULL(e.emp_name, w.user_code) AS real_name, " +
            "ISNULL(e.emp_code, w.user_code) AS emp_code, " +
            "w.indicator_name AS indicator_name, " +
            "w.indicator_value AS indicator_value, " +
            "w.warning_level AS warning_level, " +
            "w.is_handled AS handled, " +
            "w.create_time AS create_time " +
            "FROM ${warningSource} AS w " +
            "LEFT JOIN employee e ON w.user_code = e.emp_code " +
            "WHERE w.create_time >= CONVERT(date, #{startTime}) " +
            "AND   w.create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "ORDER BY w.create_time DESC")
    List<DashboardWarningEventRow> getWarningsByRangeDirect(@Param("warningSource") String warningSource,
                                                            @Param("limit")         int    limit,
                                                            @Param("startTime")     String startTime,
                                                            @Param("endTime")       String endTime);
}
