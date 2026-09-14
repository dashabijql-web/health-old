package com.xzkj.health.mapper;

import com.xzkj.health.dto.dashboard.DashboardDayBloodOxygenRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayHeartRateRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayStepsRankRow;
import com.xzkj.health.dto.dashboard.DashboardDayWarningRow;
import com.xzkj.health.dto.dashboard.DashboardDailyHealthTrendRow;
import com.xzkj.health.dto.dashboard.DashboardWarningCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Dashboard calendar and day-drilldown queries.
 */
@Mapper
public interface DashboardCalendarMapper {

    @Select("SELECT CONVERT(VARCHAR(10), create_time, 120) AS statDate, COUNT(DISTINCT user_code) AS cnt " +
            "FROM ${warningSource} AS wr_src " +
            "WHERE create_time >= CONVERT(date, #{startTime}) " +
            "AND   create_time <  DATEADD(DAY, 1, CONVERT(date, #{endTime})) " +
            "GROUP BY CONVERT(VARCHAR(10), create_time, 120) " +
            "ORDER BY statDate ASC")
    List<DashboardWarningCountRow> getWarningCountsByDateDirect(@Param("warningSource") String warningSource,
                                                                @Param("startTime")     String startTime,
                                                                @Param("endTime")       String endTime);

    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 120) AS date, " +
            "ROUND(AVG(CAST(heart_rate   AS FLOAT)), 0) AS avgHeartRate, " +
            "ROUND(AVG(CAST(blood_oxygen AS FLOAT)), 0) AS avgBloodOxygen, " +
            "ROUND(AVG(CAST(steps        AS FLOAT)), 0) AS avgSteps " +
            "FROM ${healthSource} AS hr_src " +
            "WHERE record_time >= CONVERT(date, #{startDate}) " +
            "AND   record_time <  DATEADD(DAY, 1, CONVERT(date, #{endDate})) " +
            "AND (heart_rate IS NOT NULL OR blood_oxygen IS NOT NULL OR steps IS NOT NULL) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 120) " +
            "ORDER BY date ASC")
    List<DashboardDailyHealthTrendRow> getDailyHealthTrendDirect(@Param("healthSource") String healthSource,
                                                                 @Param("startDate")    String startDate,
                                                                 @Param("endDate")      String endDate);

    @Select("SELECT TOP 20 " +
            "ISNULL(e.emp_name, hr.user_code) AS empName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "CAST(ROUND(AVG(CAST(hr.heart_rate AS FLOAT)), 0) AS INT) AS avgHeartRate " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.record_time >= CONVERT(DATETIME, #{date}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "AND hr.heart_rate IS NOT NULL " +
            "GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            "ORDER BY ABS(AVG(CAST(hr.heart_rate AS FLOAT)) - 80) DESC")
    List<DashboardDayHeartRateRankRow> getDayHeartRateRank(@Param("date") String date);

    @Select("SELECT TOP 20 " +
            "ISNULL(e.emp_name, hr.user_code) AS empName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "ROUND(AVG(CAST(hr.blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.record_time >= CONVERT(DATETIME, #{date}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "AND hr.blood_oxygen IS NOT NULL " +
            "GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            "ORDER BY AVG(CAST(hr.blood_oxygen AS FLOAT)) ASC")
    List<DashboardDayBloodOxygenRankRow> getDayBloodOxygenRank(@Param("date") String date);

    @Select("SELECT TOP 20 " +
            "ISNULL(e.emp_name, hr.user_code) AS empName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "CAST(ROUND(AVG(CAST(hr.steps AS FLOAT)), 0) AS INT) AS avgSteps " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.record_time >= CONVERT(DATETIME, #{date}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "AND hr.steps IS NOT NULL " +
            "GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            "ORDER BY AVG(CAST(hr.steps AS FLOAT)) ASC")
    List<DashboardDayStepsRankRow> getDayStepsRank(@Param("date") String date);

    @Select("SELECT TOP 50 " +
            "ISNULL(e.emp_name, w.user_code) AS empName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "w.warning_type AS warningType, " +
            "w.indicator_name AS indicatorName, " +
            "w.indicator_value AS warningValue, " +
            "w.warning_level AS warningLevel, " +
            "w.create_time AS createTime " +
            "FROM v_warning_record w " +
            "LEFT JOIN employee e ON w.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE w.create_time >= CONVERT(DATETIME, #{date}) " +
            "AND w.create_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "ORDER BY CASE w.warning_level " +
            "  WHEN '危急' THEN 5 WHEN '高危' THEN 4 " +
            "  WHEN '高'   THEN 4 WHEN '危险' THEN 3 " +
            "  WHEN '中'   THEN 2 WHEN '警告' THEN 2 " +
            "  WHEN '低'   THEN 1 ELSE 0 END DESC, w.create_time DESC")
    List<DashboardDayWarningRow> getDayWarnings(@Param("date") String date);
}
