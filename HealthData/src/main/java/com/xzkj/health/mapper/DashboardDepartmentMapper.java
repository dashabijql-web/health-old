package com.xzkj.health.mapper;

import com.xzkj.health.dto.dashboard.DashboardDailyPersonRow;
import com.xzkj.health.dto.dashboard.DashboardDepartmentHealthCountRow;
import com.xzkj.health.dto.dashboard.DashboardDepartmentRankingRow;
import com.xzkj.health.dto.dashboard.DashboardHealthComparisonRow;
import com.xzkj.health.dto.dashboard.DashboardMetricCountRow;
import com.xzkj.health.dto.dashboard.DashboardPersonStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

/**
 * Dashboard 部门与人员统计查询。
 *
 * Dashboard 其他主题分别由 DashboardOverviewMapper、DashboardCalendarMapper、
 * DashboardEntryMapper 承接，避免继续把所有 Dashboard SQL 堆回历史大 mapper。
 */
@Mapper
public interface DashboardDepartmentMapper {

    @Select("SELECT d.dept_name AS name, " +
            "COUNT(DISTINCT CASE WHEN w.create_time >= CONVERT(date, #{startTime}) " +
            "  AND w.create_time < DATEADD(DAY,1,CONVERT(date,#{endTime})) THEN w.id END) AS count, " +
            "COUNT(DISTINCT CASE WHEN w.create_time >= DATEADD(DAY,-#{days},CONVERT(date,#{startTime})) " +
            "  AND w.create_time < CONVERT(date,#{startTime}) THEN w.id END) AS prevCount " +
            "FROM department d " +
            "LEFT JOIN employee e ON d.id = e.dept_id AND (e.status IS NULL OR e.status = 0) " +
            "LEFT JOIN ${warningSource} AS w ON e.emp_code = w.user_code " +
            "GROUP BY d.dept_name " +
            "HAVING COUNT(DISTINCT CASE WHEN w.create_time >= CONVERT(date, #{startTime}) " +
            "  AND w.create_time < DATEADD(DAY,1,CONVERT(date,#{endTime})) THEN w.id END) > 0 " +
            "ORDER BY count DESC")
    List<DashboardDepartmentHealthCountRow> getDeptWarningWithTrendDirect(@Param("warningSource") String warningSource,
                                                                          @Param("startTime")     String startTime,
                                                                          @Param("endTime")       String endTime,
                                                                          @Param("days")          int    days);

    @Select("SELECT TOP 10 " +
            "d.dept_name AS department, " +
            "COUNT(DISTINCT e.id) AS memberCount, " +
            "CASE WHEN ISNULL(h.record_count, 0) = 0 THEN 75 " +
            "     WHEN ISNULL(w.warning_count, 0) >= h.record_count THEN 0 " +
            "     ELSE CAST(100 - ISNULL(w.warning_count, 0) * 100 / h.record_count AS INT) " +
            "END AS healthScore " +
            "FROM department d " +
            "INNER JOIN employee e ON d.id = e.dept_id AND (e.status IS NULL OR e.status = 0) " +
            "JOIN ( " +
            "  SELECT user_code, COUNT(id) AS record_count " +
            "  FROM v_health_record " +
            "  WHERE record_time >= CONVERT(date, #{startTime}) " +
            "  AND   record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY user_code " +
            ") h ON h.user_code = e.emp_code " +
            "LEFT JOIN ( " +
            "  SELECT user_code, COUNT(id) AS warning_count " +
            "  FROM v_warning_record " +
            "  WHERE create_time >= CONVERT(date, #{startTime}) " +
            "  AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY user_code " +
            ") w ON w.user_code = e.emp_code " +
            "GROUP BY d.id, d.dept_name, h.record_count, w.warning_count " +
            "ORDER BY healthScore DESC")
    List<DashboardDepartmentRankingRow> getDeptRanking(@Param("startTime") String startTime,
                                                       @Param("endTime")   String endTime);

    @Select("SELECT SUM(has_hr) AS heartRate, SUM(has_bo) AS bloodOxygen, " +
            "SUM(has_st) AS steps, SUM(has_tp) AS temperature, " +
            "SUM(has_pr) AS pressure, COUNT(*) AS totalPersons " +
            "FROM ( " +
            "  SELECT user_code, " +
            "    MAX(CASE WHEN heart_rate   IS NOT NULL THEN 1 ELSE 0 END) AS has_hr, " +
            "    MAX(CASE WHEN blood_oxygen IS NOT NULL THEN 1 ELSE 0 END) AS has_bo, " +
            "    MAX(CASE WHEN steps        IS NOT NULL THEN 1 ELSE 0 END) AS has_st, " +
            "    MAX(CASE WHEN temperature  IS NOT NULL THEN 1 ELSE 0 END) AS has_tp, " +
            "    MAX(CASE WHEN pressure     IS NOT NULL THEN 1 ELSE 0 END) AS has_pr " +
            "  FROM ${healthSource} AS hr_src " +
            "  WHERE record_time >= CONVERT(date, #{startTime}) " +
            "  AND   record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY user_code " +
            ") AS user_flags")
    DashboardMetricCountRow getPersonCountsByRangeDirect(@Param("healthSource") String healthSource,
                                                         @Param("startTime")    String startTime,
                                                         @Param("endTime")      String endTime);

    @Select("SELECT " +
            "COUNT(DISTINCT CASE WHEN heart_rate_samples > 0 THEN user_code END) AS heartRate, " +
            "COUNT(DISTINCT CASE WHEN blood_oxygen_samples > 0 THEN user_code END) AS bloodOxygen, " +
            "COUNT(DISTINCT CASE WHEN sleep_samples > 0 THEN user_code END) AS sleep, " +
            "COUNT(DISTINCT CASE WHEN steps_samples > 0 THEN user_code END) AS steps, " +
            "COUNT(DISTINCT CASE WHEN temperature_samples > 0 THEN user_code END) AS temperature, " +
            "COUNT(DISTINCT CASE WHEN pressure_samples > 0 THEN user_code END) AS pressure, " +
            "COUNT(DISTINCT user_code) AS totalPersons " +
            "FROM health_user_daily_summary " +
            "WHERE stat_date >= CONVERT(date, #{startTime}) " +
            "AND stat_date <= CONVERT(date, #{endTime})")
    DashboardMetricCountRow getPersonCountsFromDailySummary(@Param("startTime") String startTime,
                                                             @Param("endTime") String endTime);

    @Select("SELECT COUNT(*) FROM dashboard_daily_refresh_state " +
            "WHERE stat_date >= CONVERT(date, #{startTime}) " +
            "AND stat_date <= CONVERT(date, #{endTime})")
    int countRefreshedSummaryDays(@Param("startTime") String startTime,
                                  @Param("endTime") String endTime);

    @Select("SELECT d.dept_name AS deptName, " +
            "CONVERT(VARCHAR(10), hr.record_time, 120) AS day, " +
            "COUNT(DISTINCT e.emp_code) AS personCount " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e  ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id   = d.id " +
            "WHERE hr.record_time >= CONVERT(date, #{startTime}) " +
            "AND   hr.record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "GROUP BY d.dept_name, CONVERT(VARCHAR(10), hr.record_time, 120) " +
            "ORDER BY d.dept_name, day")
    List<DashboardDailyPersonRow> getDeptDailyPersons(@Param("startTime") String startTime,
                                                      @Param("endTime")   String endTime);

    @Select("SELECT dp.deptName, dp.personCount, " +
            "ISNULL(da.abnormalPersonCount, 0) AS abnormalPersonCount " +
            "FROM ( " +
            "  SELECT d.dept_name AS deptName, COUNT(DISTINCT hr.user_code) AS personCount " +
            "  FROM ${healthSource} AS hr " +
            "  INNER JOIN employee e   ON hr.user_code = e.emp_code " +
            "  INNER JOIN department d ON e.dept_id    = d.id " +
            "  WHERE hr.record_time >= CONVERT(date, #{startTime}) " +
            "  AND   hr.record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY d.dept_name " +
            ") AS dp " +
            "LEFT JOIN ( " +
            "  SELECT d.dept_name AS deptName, COUNT(DISTINCT wr.user_code) AS abnormalPersonCount " +
            "  FROM ${warningSource} AS wr " +
            "  INNER JOIN employee e   ON wr.user_code = e.emp_code " +
            "  INNER JOIN department d ON e.dept_id    = d.id " +
            "  WHERE wr.create_time >= CONVERT(date, #{startTime}) " +
            "  AND   wr.create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY d.dept_name " +
            ") AS da ON dp.deptName = da.deptName " +
            "ORDER BY dp.personCount DESC")
    List<DashboardPersonStatRow> getDeptPersonStatsDirect(@Param("healthSource")  String healthSource,
                                                          @Param("warningSource") String warningSource,
                                                          @Param("startTime")     String startTime,
                                                          @Param("endTime")       String endTime);

    @Select("SELECT hp.deptName, hp.personCount, ISNULL(wp.abnormalPersonCount, 0) AS abnormalPersonCount " +
            "FROM (" +
            "  SELECT d.dept_name AS deptName, COUNT(DISTINCT h.user_code) AS personCount " +
            "  FROM health_user_daily_summary h " +
            "  INNER JOIN employee e ON e.emp_code = h.user_code " +
            "  INNER JOIN department d ON d.id = e.dept_id " +
            "  WHERE h.stat_date >= CONVERT(date, #{startTime}) AND h.stat_date <= CONVERT(date, #{endTime}) " +
            "  GROUP BY d.dept_name" +
            ") hp LEFT JOIN (" +
            "  SELECT d.dept_name AS deptName, COUNT(DISTINCT w.user_code) AS abnormalPersonCount " +
            "  FROM warning_user_daily_summary w " +
            "  INNER JOIN department d ON d.id = w.dept_id " +
            "  WHERE w.stat_date >= CONVERT(date, #{startTime}) AND w.stat_date <= CONVERT(date, #{endTime}) " +
            "  GROUP BY d.dept_name" +
            ") wp ON wp.deptName = hp.deptName ORDER BY hp.personCount DESC")
    List<DashboardPersonStatRow> getDeptPersonStatsFromDailySummary(@Param("startTime") String startTime,
                                                                    @Param("endTime") String endTime);

    @Select("SELECT dp.day, dp.personCount, ISNULL(da.abnormalPersonCount, 0) AS abnormalPersonCount " +
            "FROM ( " +
            "  SELECT CONVERT(VARCHAR(10), hr.record_time, 120) AS day, " +
            "  COUNT(DISTINCT e.emp_code) AS personCount " +
            "  FROM ${healthSource} AS hr " +
            "  INNER JOIN employee e   ON hr.user_code = e.emp_code " +
            "  INNER JOIN department d ON e.dept_id    = d.id " +
            "  WHERE d.dept_name = #{deptName} " +
            "  AND hr.record_time >= CONVERT(date, #{startTime}) " +
            "  AND hr.record_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY CONVERT(VARCHAR(10), hr.record_time, 120) " +
            ") AS dp " +
            "LEFT JOIN ( " +
            "  SELECT CONVERT(VARCHAR(10), wr.create_time, 120) AS day, " +
            "  COUNT(DISTINCT wr.user_code) AS abnormalPersonCount " +
            "  FROM ${warningSource} AS wr " +
            "  INNER JOIN employee e   ON wr.user_code = e.emp_code " +
            "  INNER JOIN department d ON e.dept_id    = d.id " +
            "  WHERE d.dept_name = #{deptName} " +
            "  AND wr.create_time >= CONVERT(date, #{startTime}) " +
            "  AND wr.create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "  GROUP BY CONVERT(VARCHAR(10), wr.create_time, 120) " +
            ") AS da ON dp.day = da.day " +
            "ORDER BY dp.day")
    List<DashboardPersonStatRow> getDeptDailyDetail(@Param("healthSource")  String healthSource,
                                                    @Param("warningSource") String warningSource,
                                                    @Param("deptName")      String deptName,
                                                    @Param("startTime")     String startTime,
                                                    @Param("endTime")       String endTime);

    @SelectProvider(type = MetricDailySqlProvider.class, method = "getMetricDailyDetail")
    List<DashboardPersonStatRow> getMetricDailyDetail(@Param("tableSource") String tableSource,
                                                      @Param("metricType")  String metricType,
                                                      @Param("startTime")   String startTime,
                                                      @Param("endTime")     String endTime);

    @Select("SELECT " +
            "  d.dept_name AS deptName, " +
            "  COUNT(DISTINCT hr.user_code) AS memberCount, " +
            "  ROUND(AVG(CAST(hr.heart_rate AS FLOAT)), 1) AS avgHeartRate, " +
            "  ROUND(AVG(CAST(hr.blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen, " +
            "  ROUND(AVG(CAST(hr.blood_pressure_high AS FLOAT)), 1) AS avgSystolic, " +
            "  ROUND(AVG(CAST(hr.sleep_minutes AS FLOAT)), 0) AS avgSleepMinutes, " +
            "  ROUND(AVG(CAST(hr.steps AS FLOAT)), 0) AS avgSteps, " +
            "  ROUND(AVG(CAST(hr.pressure AS FLOAT)), 1) AS avgPressure " +
            "FROM ${tableSource} hr " +
            "JOIN employee e ON hr.user_code = e.emp_code " +
            "JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY d.dept_name " +
            "HAVING COUNT(DISTINCT hr.user_code) >= 2 " +
            "ORDER BY memberCount DESC")
    List<DashboardHealthComparisonRow> getDeptHealthComparisonDirect(@Param("tableSource") String tableSource,
                                                                     @Param("days") int days);
}
