package com.xzkj.health.mapper;

import com.xzkj.health.dto.pressure.PressureAbnormalRecordRow;
import com.xzkj.health.dto.pressure.PressureDepartmentStatRow;
import com.xzkj.health.dto.pressure.PressureDistributionRow;
import com.xzkj.health.dto.pressure.PressureHourlyRow;
import com.xzkj.health.dto.pressure.PressureOverviewRow;
import com.xzkj.health.dto.pressure.PressureRealtimeRow;
import com.xzkj.health.dto.pressure.PressureTopUserRow;
import com.xzkj.health.dto.pressure.PressureTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 压力指数分析 Mapper
 * 字段：pressure（0-100，正常 < 70，偏高 70-84，高压 >= 85）
 */
@Mapper
public interface PressureMapper {

    /** 概览统计 */
    @Select("SELECT " +
            "COALESCE(CAST(AVG(CAST(pressure AS FLOAT)) AS INT), 0) AS avg_pressure, " +
            "COALESCE(MIN(pressure), 0) AS min_pressure, " +
            "COALESCE(MAX(pressure), 0) AS max_pressure, " +
            "COUNT(DISTINCT user_code) AS detection_count, " +
            "COUNT(*) AS total_count, " +
            "COALESCE(CAST(SUM(CASE WHEN pressure < 70 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS INT), 0) AS normal_rate, " +
            "COALESCE(SUM(CASE WHEN pressure >= 70 THEN 1 ELSE 0 END), 0) AS abnormal_count, " +
            "COALESCE(SUM(CASE WHEN pressure >= 85 THEN 1 ELSE 0 END), 0) AS high_count " +
            "FROM v_health_record " +
            "WHERE pressure IS NOT NULL AND pressure > 0 " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate}))")
    PressureOverviewRow getOverview(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 每日趋势 */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "CAST(AVG(CAST(pressure AS FLOAT)) AS INT) AS avg_pressure " +
            "FROM v_health_record " +
            "WHERE pressure IS NOT NULL AND pressure > 0 " +
            "AND record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<PressureTrendRow> getTrend(@Param("days") int days);

    /** 分布统计：正常 / 偏高 / 高压 */
    @Select("SELECT category AS name, CAST(cnt * 100.0 / NULLIF(SUM(cnt) OVER(), 0) AS INT) AS value, color " +
            "FROM ( " +
            "  SELECT " +
            "    CASE WHEN pressure < 70 THEN '正常' WHEN pressure < 85 THEN '偏高' ELSE '高压' END AS category, " +
            "    CASE WHEN pressure < 70 THEN '#66BB6A' WHEN pressure < 85 THEN '#FFB84D' ELSE '#F06292' END AS color, " +
            "    COUNT(*) AS cnt " +
            "  FROM v_health_record " +
            "  WHERE pressure IS NOT NULL AND pressure > 0 " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY " +
            "    CASE WHEN pressure < 70 THEN '正常' WHEN pressure < 85 THEN '偏高' ELSE '高压' END, " +
            "    CASE WHEN pressure < 70 THEN '#66BB6A' WHEN pressure < 85 THEN '#FFB84D' ELSE '#F06292' END " +
            ") AS ps " +
            "ORDER BY CASE category WHEN '正常' THEN 1 WHEN '偏高' THEN 2 ELSE 3 END")
    List<PressureDistributionRow> getDistribution(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 分布统计 — 直接查分区表，避免扫 v_health_record UNION ALL。 */
    @Select("SELECT category AS name, CAST(cnt * 100.0 / NULLIF(SUM(cnt) OVER(), 0) AS INT) AS value, color " +
            "FROM ( " +
            "  SELECT " +
            "    CASE WHEN pressure < 70 THEN '正常' WHEN pressure < 85 THEN '偏高' ELSE '高压' END AS category, " +
            "    CASE WHEN pressure < 70 THEN '#66BB6A' WHEN pressure < 85 THEN '#FFB84D' ELSE '#F06292' END AS color, " +
            "    COUNT(*) AS cnt " +
            "  FROM ${tableSource} " +
            "  WHERE pressure IS NOT NULL AND pressure > 0 " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY " +
            "    CASE WHEN pressure < 70 THEN '正常' WHEN pressure < 85 THEN '偏高' ELSE '高压' END, " +
            "    CASE WHEN pressure < 70 THEN '#66BB6A' WHEN pressure < 85 THEN '#FFB84D' ELSE '#F06292' END " +
            ") AS ps " +
            "ORDER BY CASE category WHEN '正常' THEN 1 WHEN '偏高' THEN 2 ELSE 3 END")
    List<PressureDistributionRow> getDistributionDirect(@Param("tableSource") String tableSource,
                                                        @Param("startDate") String startDate,
                                                        @Param("endDate") String endDate);

    /** TOP N 高压力人员 */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS user_code, " +
            "ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "ISNULL(d.dept_name, '') AS dept_name, " +
            "CAST(AVG(CAST(hr.pressure AS FLOAT)) AS INT) AS avg_pressure, " +
            "MAX(hr.pressure) AS max_pressure, " +
            "COUNT(*) AS count " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.pressure IS NOT NULL AND hr.pressure >= 70 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            "ORDER BY avg_pressure DESC")
    List<PressureTopUserRow> getTopUsers(@Param("limit") int limit,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);

    /** 部门压力统计 */
    @Select("SELECT " +
            "d.dept_name AS dept_name, " +
            "CAST(AVG(CAST(hr.pressure AS FLOAT)) AS INT) AS avg_pressure, " +
            "SUM(CASE WHEN hr.pressure >= 85 THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN hr.pressure >= 70 THEN 1 ELSE 0 END) AS abnormal_count, " +
            "COUNT(*) AS total_count " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.pressure IS NOT NULL AND hr.pressure > 0 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY d.dept_name " +
            "ORDER BY avg_pressure DESC")
    List<PressureDepartmentStatRow> getDepartmentStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 异常压力记录（分页） */
    @Select("SELECT " +
            "hr.user_code AS user_code, " +
            "ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "ISNULL(d.dept_name, '') AS dept_name, " +
            "hr.pressure, " +
            "CASE WHEN hr.pressure >= 85 THEN 'danger' ELSE 'warning' END AS level, " +
            "hr.record_time AS record_time " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.pressure >= 70 AND hr.pressure IS NOT NULL " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY hr.record_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<PressureAbnormalRecordRow> getAbnormalRecords(@Param("offset") int offset, @Param("size") int size);

    /** 异常记录总数 */
    @Select("SELECT COUNT(*) FROM v_health_record " +
            "WHERE pressure >= 70 AND pressure IS NOT NULL " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    int countAbnormalRecords();

    /** 实时压力列表（近2小时每人最新一条） */
    @Select("SELECT TOP (#{limit}) user_code, user_name, dept_name, pressure, record_time " +
            "FROM (" +
            "  SELECT hr.user_code AS user_code, " +
            "    ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "    ISNULL(d.dept_name, '') AS dept_name, " +
            "    hr.pressure, hr.record_time AS record_time, " +
            "    ROW_NUMBER() OVER (PARTITION BY hr.user_code ORDER BY hr.record_time DESC) AS rn " +
            "  FROM v_health_record hr " +
            "  LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "  LEFT JOIN department d ON e.dept_id = d.id " +
            "  WHERE hr.pressure IS NOT NULL AND hr.pressure > 0 " +
            "  AND hr.record_time >= DATEADD(HOUR, -2, GETDATE())" +
            ") latest WHERE rn = 1 ORDER BY record_time DESC")
    List<PressureRealtimeRow> getRealtime(@Param("limit") int limit);

    /** 指定日期的每小时均值 */
    @Select("SELECT DATEPART(HOUR, record_time) AS hour, " +
            "CAST(AVG(CAST(pressure AS FLOAT)) AS INT) AS avg_pressure " +
            "FROM v_health_record " +
            "WHERE pressure IS NOT NULL AND pressure > 0 " +
            "AND record_time >= CONVERT(DATETIME, #{date}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "GROUP BY DATEPART(HOUR, record_time) " +
            "ORDER BY hour")
    List<PressureHourlyRow> getHourlyStats(@Param("date") String date);
}
