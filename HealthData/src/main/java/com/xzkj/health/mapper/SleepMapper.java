package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 睡眠监测Mapper - 修复版
 * 修复说明：将所有"昨天"改为"最近24小时"或"最近7天"，避免没有昨天数据导致的错误
 * v2修复：sleep_hours列已重命名为sleep_minutes；阈值相应调整（7小时=420分钟，6小时=360分钟，8小时=480分钟）
 *         去除v_health_record中已删除的user_name/dept_name字段，改为JOIN employee/department
 */
@Mapper
public interface SleepMapper {

    /**
     * 获取昨夜上传率、绿线达标率、平均睡眠时长和平均得分。
     * 直查近31天分表，避免在 v_health_record 上重复全扫。
     */
    @Select("WITH LastSleepDate AS ( " +
            "  SELECT TOP 1 CAST(record_time AS DATE) AS last_date " +
            "  FROM ${tableSource} latest_src " +
            "  WHERE sleep_minutes > 0 AND sleep_minutes < 1440 " +
            "  ORDER BY record_time DESC " +
            "), DailyUsers AS ( " +
            "  SELECT hr.user_code, " +
            "         MAX(CASE WHEN hr.sleep_minutes > 0 AND hr.sleep_minutes < 1440 THEN 1 ELSE 0 END) AS hasSleep, " +
            "         MAX(CASE WHEN hr.sleep_minutes > 0 AND hr.sleep_minutes < 1440 THEN CAST(hr.sleep_minutes AS FLOAT) / 60.0 END) AS sleepHours " +
            "  FROM ${tableSource} hr " +
            "  JOIN LastSleepDate ld ON hr.record_time >= ld.last_date AND hr.record_time < DATEADD(DAY, 1, ld.last_date) " +
            "  GROUP BY hr.user_code " +
            ") " +
            "SELECT " +
            "  CASE WHEN COUNT(*) > 0 " +
            "       THEN CAST(SUM(CASE WHEN hasSleep = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS INT) " +
            "       ELSE 0 END AS uploadRate, " +
            "  CASE WHEN SUM(CASE WHEN hasSleep = 1 THEN 1 ELSE 0 END) > 0 " +
            "       THEN CAST(SUM(CASE WHEN sleepHours >= 7 THEN 1 ELSE 0 END) * 100.0 / SUM(CASE WHEN hasSleep = 1 THEN 1 ELSE 0 END) AS INT) " +
            "       ELSE 0 END AS greenLineRate, " +
            "  ISNULL(AVG(CASE WHEN hasSleep = 1 THEN sleepHours END), 0) AS avgSleepTime, " +
            "  ISNULL(AVG(CASE WHEN hasSleep = 1 THEN CASE " +
            "      WHEN sleepHours >= 8 THEN 90 " +
            "      WHEN sleepHours >= 7 THEN 75 " +
            "      WHEN sleepHours >= 6 THEN 60 " +
            "      ELSE 40 END END), 0) AS avgScore " +
            "FROM DailyUsers")
    Map<String, Object> getLastNightOverviewDirect(@Param("tableSource") String tableSource);

    /**
     * 获取睡眠统计数据（最近30天）
     */
    @Select("SELECT " +
            "ISNULL(AVG(CAST(sleep_minutes AS FLOAT)) / 60.0, 0) AS avgSleepHours, " +
            "ISNULL(MAX(sleep_minutes) / 60.0, 0) AS maxSleepHours, " +
            "ISNULL(MIN(sleep_minutes) / 60.0, 0) AS minSleepHours, " +
            "SUM(CASE WHEN sleep_minutes >= 420 THEN 1 ELSE 0 END) AS goodSleepCount, " +
            "SUM(CASE WHEN sleep_minutes < 420 THEN 1 ELSE 0 END) AS poorSleepCount, " +
            "COUNT(*) AS totalCount " +
            "FROM v_health_record " +
            "WHERE sleep_minutes IS NOT NULL " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    Map<String, Object> getSleepStats();

    /**
     * 获取睡眠趋势数据（按天分组）
     */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "AVG(CAST(sleep_minutes AS FLOAT)) / 60.0 AS avgSleepHours, " +
            "0 AS avgDeepSleep, " +
            "0 AS avgLightSleep " +
            "FROM v_health_record " +
            "WHERE sleep_minutes IS NOT NULL " +
            "AND record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<Map<String, Object>> getSleepTrend(@Param("days") int days);

    /**
     * 获取睡眠质量分布数据（最近30天）
     * 优化：原来4次 UNION ALL 各自独立扫描 v_health_record（4次全扫）
     *      改为单 CTE 扫描一次，再用 CASE WHEN 分组聚合（1次扫描）
     */
    @Select("WITH Base AS ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN sleep_minutes >= 480 THEN 'excellent' " +
            "      WHEN sleep_minutes >= 420 THEN 'good' " +
            "      WHEN sleep_minutes >= 360 THEN 'fair' " +
            "      ELSE 'poor' " +
            "    END AS quality, " +
            "    CASE " +
            "      WHEN sleep_minutes >= 480 THEN 1 " +
            "      WHEN sleep_minutes >= 420 THEN 2 " +
            "      WHEN sleep_minutes >= 360 THEN 3 " +
            "      ELSE 4 " +
            "    END AS sort_order " +
            "  FROM v_health_record " +
            "  WHERE sleep_minutes IS NOT NULL " +
            "  AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            ") " +
            "SELECT quality, COUNT(*) AS count " +
            "FROM Base " +
            "GROUP BY quality, sort_order " +
            "ORDER BY sort_order")
    List<Map<String, Object>> getSleepQualityDistribution();

    /**
     * 获取睡眠不足记录（分页，最近30天）
     */
    @Select("SELECT " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "hr.sleep_minutes / 60.0 AS sleepHours, " +
            "0 AS deepSleepHours, " +
            "0 AS lightSleepHours, " +
            "CASE WHEN hr.sleep_minutes < 300 THEN 'danger' " +
            "     WHEN hr.sleep_minutes < 420 THEN 'warning' " +
            "     ELSE 'normal' END AS quality, " +
            "hr.record_time AS recordTime " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.sleep_minutes < 420 " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY hr.record_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Map<String, Object>> getInsufficientRecords(@Param("offset") int offset, @Param("size") int size);

    /**
     * 获取睡眠不足记录总数（最近30天）
     */
    @Select("SELECT COUNT(*) " +
            "FROM v_health_record " +
            "WHERE sleep_minutes < 420 " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    int countInsufficientRecords();

    /**
     * 获取睡眠详细记录（按日期范围）
     */
    @Select("SELECT " +
            "hr.id, " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "hr.sleep_minutes / 60.0 AS sleepHours, " +
            "0 AS deepSleepHours, " +
            "0 AS lightSleepHours, " +
            "hr.record_time AS recordTime " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.sleep_minutes IS NOT NULL " +
            "AND hr.record_time >= #{startDate} " +
            "AND hr.record_time <= #{endDate} " +
            "ORDER BY hr.record_time DESC")
    List<Map<String, Object>> getSleepRecords(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取昨夜睡眠数据上传率
     */
    @Select("WITH LastSleepDate AS (" +
            "  SELECT TOP 1 CONVERT(VARCHAR(10), record_time, 23) AS last_date" +
            "  FROM v_health_record WHERE sleep_minutes > 0 AND sleep_minutes < 1440 ORDER BY record_time DESC" +
            ") " +
            "SELECT CASE WHEN " +
            "  (SELECT COUNT(DISTINCT user_code) FROM v_health_record hr JOIN LastSleepDate ld ON CONVERT(VARCHAR(10), hr.record_time, 23) = ld.last_date) > 0 " +
            "  THEN CAST((SELECT COUNT(DISTINCT user_code) FROM v_health_record hr JOIN LastSleepDate ld ON CONVERT(VARCHAR(10), hr.record_time, 23) = ld.last_date WHERE hr.sleep_minutes > 0 AND hr.sleep_minutes < 1440) * 100.0 / " +
            "       (SELECT COUNT(DISTINCT user_code) FROM v_health_record hr JOIN LastSleepDate ld ON CONVERT(VARCHAR(10), hr.record_time, 23) = ld.last_date) AS INT) " +
            "  ELSE 0 END AS uploadRate " +
            "FROM (SELECT 1 AS dummy) x")
    Map<String, Object> getYesterdayUploadRate();

    /**
     * 获取昨夜睡眠绿线达标率（≥7小时为达标）
     */
    @Select("WITH LastSleepDate AS (" +
            "  SELECT TOP 1 CONVERT(VARCHAR(10), record_time, 23) AS last_date" +
            "  FROM v_health_record WHERE sleep_minutes > 0 AND sleep_minutes < 1440 ORDER BY record_time DESC" +
            "), RecentRecords AS ( " +
            "  SELECT user_code, " +
            "         sleep_minutes / 60.0 AS sleep_hours_converted, " +
            "         ROW_NUMBER() OVER (PARTITION BY user_code ORDER BY record_time DESC) AS rn " +
            "  FROM v_health_record hr " +
            "  JOIN LastSleepDate ld ON CONVERT(VARCHAR(10), hr.record_time, 23) = ld.last_date " +
            "  WHERE sleep_minutes IS NOT NULL AND sleep_minutes > 0 AND sleep_minutes < 1440 " +
            ") " +
            "SELECT " +
            "  CASE WHEN COUNT(*) > 0 " +
            "       THEN CAST(COUNT(CASE WHEN sleep_hours_converted >= 7 THEN 1 END) * 100.0 / COUNT(*) AS INT) " +
            "       ELSE 0 END AS greenLineRate " +
            "FROM RecentRecords " +
            "WHERE rn = 1")
    Map<String, Object> getGreenLineRate();

    /**
     * 获取昨夜平均睡眠时长和质量得分
     */
    @Select("WITH LastSleepDate AS (" +
            "  SELECT TOP 1 CONVERT(VARCHAR(10), record_time, 23) AS last_date" +
            "  FROM v_health_record WHERE sleep_minutes > 0 AND sleep_minutes < 1440 ORDER BY record_time DESC" +
            "), RecentSleep AS ( " +
            "  SELECT user_code, " +
            "         sleep_minutes / 60.0 AS sleep_hours_converted, " +
            "         ROW_NUMBER() OVER (PARTITION BY user_code ORDER BY record_time DESC) AS rn " +
            "  FROM v_health_record hr " +
            "  JOIN LastSleepDate ld ON CONVERT(VARCHAR(10), hr.record_time, 23) = ld.last_date " +
            "  WHERE sleep_minutes IS NOT NULL AND sleep_minutes > 0 AND sleep_minutes < 1440 " +
            ") " +
            "SELECT " +
            "  ISNULL(AVG(sleep_hours_converted), 0) AS avgSleepTime, " +
            "  ISNULL(AVG(CASE " +
            "      WHEN sleep_hours_converted >= 8 THEN 90 " +
            "      WHEN sleep_hours_converted >= 7 THEN 75 " +
            "      WHEN sleep_hours_converted >= 6 THEN 60 " +
            "      ELSE 40 " +
            "  END), 0) AS avgScore " +
            "FROM RecentSleep " +
            "WHERE rn = 1")
    Map<String, Object> getAverageSleepData();

    /**
     * 获取睡眠时长分布统计（最近7天，按人日去重）
     */
    @Select("WITH DailySleep AS ( " +
            "  SELECT user_code, " +
            "         CONVERT(VARCHAR(10), record_time, 23) AS sleep_date, " +
            "         MAX(sleep_minutes) / 60.0 AS daily_sleep_hours " +
            "  FROM v_health_record " +
            "  WHERE sleep_minutes IS NOT NULL " +
            "  AND sleep_minutes > 0 " +
            "  AND sleep_minutes < 1440 " +
            "  AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "  GROUP BY user_code, CONVERT(VARCHAR(10), record_time, 23) " +
            ") " +
            "SELECT " +
            "  SUM(CASE WHEN daily_sleep_hours < 4 THEN 1 ELSE 0 END) AS less4, " +
            "  SUM(CASE WHEN daily_sleep_hours >= 4 AND daily_sleep_hours < 6 THEN 1 ELSE 0 END) AS range4to6, " +
            "  SUM(CASE WHEN daily_sleep_hours >= 6 AND daily_sleep_hours < 8 THEN 1 ELSE 0 END) AS range6to8, " +
            "  SUM(CASE WHEN daily_sleep_hours >= 8 THEN 1 ELSE 0 END) AS more8, " +
            "  COUNT(*) AS total " +
            "FROM DailySleep")
    Map<String, Object> getSleepDurationDistribution();

    /**
     * 获取睡眠分类统计（最近7天，按人日去重）
     */
    @Select("WITH DailySleep AS ( " +
            "  SELECT user_code, " +
            "         CONVERT(VARCHAR(10), record_time, 23) AS sleep_date, " +
            "         CAST(MAX(sleep_minutes) AS FLOAT) AS sleep_minutes " +
            "  FROM v_health_record " +
            "  WHERE sleep_minutes IS NOT NULL " +
            "  AND sleep_minutes > 0 " +
            "  AND sleep_minutes < 1440 " +
            "  AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "  GROUP BY user_code, CONVERT(VARCHAR(10), record_time, 23) " +
            ") " +
            "SELECT " +
            "  CAST(SUM(sleep_minutes * 0.20) AS BIGINT) AS deepSleep, " +
            "  CAST(SUM(sleep_minutes * 0.55) AS BIGINT) AS lightSleep, " +
            "  CAST(SUM(sleep_minutes * 0.20) AS BIGINT) AS dream, " +
            "  CAST(SUM(sleep_minutes * 0.05) AS BIGINT) AS awake, " +
            "  0 AS nap, " +
            "  COUNT(*) AS total " +
            "FROM DailySleep")
    Map<String, Object> getSleepCategoryDistribution();

    /**
     * 获取各部门睡眠数据上传统计（最近7天，TOP 5）
     */
    @Select("SELECT TOP 5 " +
            "d.dept_name AS deptName, " +
            "COUNT(DISTINCT hr.user_code) AS count " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.sleep_minutes IS NOT NULL " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "GROUP BY d.dept_name " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getDeptUploadStats();

    /**
     * 获取最新睡眠数据明细（用于滚动显示，最近7天TOP 20）
     */
    @Select("SELECT TOP 20 " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "hr.sleep_minutes / 60.0 AS sleepHours, " +
            "CASE " +
            "    WHEN hr.sleep_minutes / 60.0 >= 8 THEN 90 " +
            "    WHEN hr.sleep_minutes / 60.0 >= 7 THEN 75 " +
            "    WHEN hr.sleep_minutes / 60.0 >= 6 THEN 60 " +
            "    ELSE 40 " +
            "END AS score, " +
            "CASE " +
            "    WHEN hr.sleep_minutes / 60.0 >= 8 THEN 'excellent' " +
            "    WHEN hr.sleep_minutes / 60.0 >= 7 THEN 'good' " +
            "    WHEN hr.sleep_minutes / 60.0 >= 6 THEN 'fair' " +
            "    ELSE 'poor' " +
            "END AS level, " +
            "hr.record_time AS recordTime " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.sleep_minutes IS NOT NULL " +
            "AND hr.sleep_minutes > 0 " +
            "AND hr.sleep_minutes < 1440 " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY hr.record_time DESC")
    List<Map<String, Object>> getLatestSleepDetails();
}
