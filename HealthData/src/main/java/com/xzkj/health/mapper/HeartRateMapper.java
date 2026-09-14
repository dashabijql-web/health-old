package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 心率监测Mapper - 增强版
 * 基于真实表结构: health_record
 * 字段: id, user_code, user_name, dept_name, heart_rate, blood_oxygen,
 *       blood_pressure_high, blood_pressure_low, temperature, sleep_hours,
 *       deep_sleep_hours, light_sleep_hours, steps, record_time, create_time, update_time
 */
@Mapper
public interface HeartRateMapper {

    /** 获取心率概览统计；人员区间按周期内最高风险状态互斥归类。 */
    @Select("SELECT " +
            "ISNULL(SUM(sum_hr) / NULLIF(SUM(cnt_hr), 0), 0) AS avgHeartRate, " +
            "ISNULL(MIN(min_hr), 0) AS minHeartRate, " +
            "ISNULL(MAX(max_hr), 0) AS maxHeartRate, " +
            "ISNULL(SUM(has_hr) * 100 / NULLIF(COUNT(*), 0), 0) AS detectionRate, " +
            "SUM(normal_cnt) AS normalCount, " +
            "SUM(abnormal_cnt) AS abnormalCount, " +
            "SUM(cnt_hr) AS totalCount, " +
            "SUM(has_hr) AS coveredUsers, " +
            "SUM(CASE WHEN min_hr < 55 OR max_hr > 120 THEN 1 ELSE 0 END) AS abnormalUsers, " +
            "SUM(CASE WHEN max_hr <= 120 AND min_hr < 55 THEN 1 ELSE 0 END) AS lowUsers, " +
            "SUM(CASE WHEN min_hr >= 55 AND max_hr <= 120 THEN 1 ELSE 0 END) AS normalUsers, " +
            "SUM(CASE WHEN max_hr > 120 AND max_hr <= 150 THEN 1 ELSE 0 END) AS elevatedUsers, " +
            "SUM(CASE WHEN max_hr > 150 THEN 1 ELSE 0 END) AS dangerUsers " +
            "FROM ( " +
            "  SELECT user_code, " +
            "    MAX(CASE WHEN heart_rate > 0 THEN 1 ELSE 0 END) AS has_hr, " +
            "    SUM(CASE WHEN heart_rate > 0 THEN heart_rate ELSE 0 END) AS sum_hr, " +
            "    COUNT(CASE WHEN heart_rate > 0 THEN 1 END) AS cnt_hr, " +
            "    MIN(CASE WHEN heart_rate > 0 THEN heart_rate END) AS min_hr, " +
            "    MAX(CASE WHEN heart_rate > 0 THEN heart_rate END) AS max_hr, " +
            "    SUM(CASE WHEN heart_rate >= 55 AND heart_rate <= 120 THEN 1 ELSE 0 END) AS normal_cnt, " +
            "    SUM(CASE WHEN heart_rate > 0 AND (heart_rate < 55 OR heart_rate > 120) THEN 1 ELSE 0 END) AS abnormal_cnt " +
            "  FROM v_health_record " +
            "  WHERE record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY user_code " +
            ") AS per_user")
    Map<String, Object> getHeartRateOverview(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取心率概览统计 — 直接查分区表，避免扫 v_health_record UNION ALL 视图
     * 性能优化（v2）：用双层子查询代替 COUNT(DISTINCT CASE WHEN ...) 跨 2.3M 行的昂贵聚合
     *   外层：汇总每人的 max/min/avg/cnt，内层：每 user_code 先聚合成1行
     *   COUNT(DISTINCT CASE WHEN) 在大表上必须全表扫，改为 GROUP BY 后 SUM 可以利用索引
     * ${tableSource} 可为单月表名（如 health_record_202603）
     */
    @Select("SELECT " +
            "ISNULL(SUM(sum_hr) / NULLIF(SUM(cnt_hr), 0), 0) AS avgHeartRate, " +
            "ISNULL(MIN(CASE WHEN min_hr > 0 THEN min_hr END), 0) AS minHeartRate, " +
            "ISNULL(MAX(max_hr), 0) AS maxHeartRate, " +
            "ISNULL(SUM(CASE WHEN has_hr = 1 THEN 1 ELSE 0 END) * 100 / NULLIF(COUNT(*), 0), 0) AS detectionRate, " +
            "SUM(normal_cnt) AS normalCount, " +
            "SUM(abnormal_cnt) AS abnormalCount, " +
            "SUM(cnt_hr) AS totalCount, " +
            "SUM(has_hr) AS coveredUsers, " +
            "SUM(CASE WHEN min_hr < 55 OR max_hr > 120 THEN 1 ELSE 0 END) AS abnormalUsers, " +
            "SUM(CASE WHEN max_hr <= 120 AND min_hr < 55 THEN 1 ELSE 0 END) AS lowUsers, " +
            "SUM(CASE WHEN min_hr >= 55 AND max_hr <= 120 THEN 1 ELSE 0 END) AS normalUsers, " +
            "SUM(CASE WHEN max_hr > 120 AND max_hr <= 150 THEN 1 ELSE 0 END) AS elevatedUsers, " +
            "SUM(CASE WHEN max_hr > 150 THEN 1 ELSE 0 END) AS dangerUsers " +
            "FROM ( " +
            "  SELECT user_code, " +
            "    MAX(CASE WHEN heart_rate > 0 THEN 1 ELSE 0 END) AS has_hr, " +
            "    SUM(CASE WHEN heart_rate > 0 THEN heart_rate ELSE 0 END) AS sum_hr, " +
            "    COUNT(CASE WHEN heart_rate > 0 THEN 1 END) AS cnt_hr, " +
            "    MIN(CASE WHEN heart_rate > 0 THEN heart_rate END) AS min_hr, " +
            "    MAX(CASE WHEN heart_rate > 0 THEN heart_rate END) AS max_hr, " +
            "    SUM(CASE WHEN heart_rate >= 55 AND heart_rate <= 120 THEN 1 ELSE 0 END) AS normal_cnt, " +
            "    SUM(CASE WHEN heart_rate > 0 AND (heart_rate < 55 OR heart_rate > 120) THEN 1 ELSE 0 END) AS abnormal_cnt " +
            "  FROM ${tableSource} " +
            "  WHERE record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY user_code " +
            ") AS per_user")
    Map<String, Object> getHeartRateOverviewDirect(@Param("tableSource") String tableSource,
                                                    @Param("startDate") String startDate,
                                                    @Param("endDate") String endDate);

    /**
     * 获取TOP N心率异常人员统计
     * 返回: userName, count (异常次数), anomalyDays (异常天数)
     */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "COUNT(*) AS count, " +
            "COUNT(DISTINCT CAST(hr.record_time AS DATE)) AS anomalyDays " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.heart_rate IS NOT NULL " +
            "AND hr.heart_rate > 0 " +
            "AND (hr.heart_rate < 55 OR hr.heart_rate > 120) " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code, e.emp_name " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getTopUsers(@Param("limit") int limit, @Param("startDate") String startDate, @Param("endDate") String endDate);

    /** TOP N心率异常人员统计 — 直接查分区表，避免扫 v_health_record UNION ALL */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "COUNT(*) AS count, " +
            "COUNT(DISTINCT CAST(hr.record_time AS DATE)) AS anomalyDays " +
            "FROM ${tableSource} hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.heart_rate IS NOT NULL " +
            "AND hr.heart_rate > 0 " +
            "AND (hr.heart_rate < 55 OR hr.heart_rate > 120) " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code, e.emp_name " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getTopUsersDirect(@Param("tableSource") String tableSource,
                                                 @Param("limit") int limit,
                                                 @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate);

    /** 按日统计心率风险：异常人数按人去重，同时返回有效覆盖和高低异常构成。 */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "COUNT(DISTINCT CASE WHEN heart_rate < 55 OR heart_rate > 120 THEN user_code END) AS anomalyCount, " +
            "COUNT(DISTINCT user_code) AS coveredUsers, " +
            "CAST(COUNT(DISTINCT CASE WHEN heart_rate < 55 OR heart_rate > 120 THEN user_code END) * 100.0 / NULLIF(COUNT(DISTINCT user_code), 0) AS DECIMAL(5,1)) AS anomalyRate, " +
            "COUNT(DISTINCT CASE WHEN heart_rate < 55 THEN user_code END) AS lowCount, " +
            "COUNT(DISTINCT CASE WHEN heart_rate > 120 THEN user_code END) AS highCount, " +
            "SUM(CASE WHEN heart_rate < 55 OR heart_rate > 120 THEN 1 ELSE 0 END) AS abnormalRecords, " +
            "COUNT(*) AS totalRecords " +
            "FROM v_health_record " +
            "WHERE heart_rate IS NOT NULL AND heart_rate > 0 " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<Map<String, Object>> getDailyAnomalyCount(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 按日统计心率风险 — 先按人/日聚合，避免 COUNT(DISTINCT) 大内存许可把 SQL Server 打满。 */
    @Select("SELECT " +
            "date, " +
            "SUM(CASE WHEN min_hr < 55 OR max_hr > 120 THEN 1 ELSE 0 END) AS anomalyCount, " +
            "COUNT(*) AS coveredUsers, " +
            "CAST(SUM(CASE WHEN min_hr < 55 OR max_hr > 120 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS DECIMAL(5,1)) AS anomalyRate, " +
            "SUM(CASE WHEN min_hr < 55 THEN 1 ELSE 0 END) AS lowCount, " +
            "SUM(CASE WHEN max_hr > 120 THEN 1 ELSE 0 END) AS highCount, " +
            "SUM(abnormal_records) AS abnormalRecords, " +
            "SUM(total_records) AS totalRecords " +
            "FROM ( " +
            "  SELECT CONVERT(VARCHAR(10), record_time, 23) AS date, user_code, " +
            "    MIN(heart_rate) AS min_hr, MAX(heart_rate) AS max_hr, " +
            "    SUM(CASE WHEN heart_rate < 55 OR heart_rate > 120 THEN 1 ELSE 0 END) AS abnormal_records, " +
            "    COUNT(*) AS total_records " +
            "  FROM ${tableSource} " +
            "  WHERE heart_rate IS NOT NULL AND heart_rate > 0 " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY CONVERT(VARCHAR(10), record_time, 23), user_code " +
            ") per_user " +
            "GROUP BY date " +
            "ORDER BY date")
    List<Map<String, Object>> getDailyAnomalyCountDirect(@Param("tableSource") String tableSource,
                                                          @Param("startDate") String startDate,
                                                          @Param("endDate") String endDate);

    /**
     * 获取年龄段心率统计（关联 employee.birth_date 计算真实年龄）
     * 返回: ageRange, avgHeartRate
     * 优化：去掉 CAST(record_time AS DATE) 函数包装，改用直接范围比较（允许索引扫描）；
     *       用 CTE 预先关联 employee，避免 GROUP BY 重复计算 DATEDIFF
     */
    @Select("SELECT ageRange, CAST(AVG(CAST(heart_rate AS FLOAT)) AS INT) AS avgHeartRate " +
            "FROM ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 30 THEN '20-30' " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 40 THEN '30-40' " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 50 THEN '40-50' " +
            "      ELSE '50+' " +
            "    END AS ageRange, " +
            "    DATEDIFF(YEAR, e.birth_date, GETDATE()) AS age, " +
            "    hr.heart_rate " +
            "  FROM v_health_record hr " +
            "  INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "  WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "  AND e.birth_date IS NOT NULL " +
            "  AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            ") AS aged_data " +
            "GROUP BY ageRange " +
            "ORDER BY MIN(age)")
    List<Map<String, Object>> getAgeDistribution(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 年龄段心率统计 — 直接查分区表，避免扫 v_health_record UNION ALL */
    @Select("SELECT ageRange, CAST(AVG(CAST(heart_rate AS FLOAT)) AS INT) AS avgHeartRate " +
            "FROM ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 30 THEN '20-30' " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 40 THEN '30-40' " +
            "      WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 50 THEN '40-50' " +
            "      ELSE '50+' " +
            "    END AS ageRange, " +
            "    DATEDIFF(YEAR, e.birth_date, GETDATE()) AS age, " +
            "    hr.heart_rate " +
            "  FROM ${tableSource} hr " +
            "  INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "  WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "  AND e.birth_date IS NOT NULL " +
            "  AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            ") AS aged_data " +
            "GROUP BY ageRange " +
            "ORDER BY MIN(age)")
    List<Map<String, Object>> getAgeDistributionDirect(@Param("tableSource") String tableSource,
                                                        @Param("startDate") String startDate,
                                                        @Param("endDate") String endDate);

    /**
     * 获取心率分布统计(新版 - 北路风格)
     * 分类: 心率偏低(<55)、心率正常(55-120)、心率偏高(>120)
     * 返回: name, value(百分比), color
     */
    @Select("SELECT " +
            "  category AS name, " +
            "  CAST(cnt * 100.0 / NULLIF(SUM(cnt) OVER(), 0) AS INT) AS value, " +
            "  color " +
            "FROM ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN heart_rate < 55 THEN '心率偏低' " +
            "      WHEN heart_rate BETWEEN 55 AND 120 THEN '心率正常' " +
            "      ELSE '心率偏高' " +
            "    END AS category, " +
            "    CASE " +
            "      WHEN heart_rate < 55 THEN '#4FC3F7' " +
            "      WHEN heart_rate BETWEEN 55 AND 120 THEN '#66BB6A' " +
            "      ELSE '#FFB84D' " +
            "    END AS color, " +
            "    COUNT(*) AS cnt " +
            "  FROM v_health_record " +
            "  WHERE heart_rate IS NOT NULL " +
            "  AND heart_rate > 0 " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY " +
            "    CASE " +
            "      WHEN heart_rate < 55 THEN '心率偏低' " +
            "      WHEN heart_rate BETWEEN 55 AND 120 THEN '心率正常' " +
            "      ELSE '心率偏高' " +
            "    END, " +
            "    CASE " +
            "      WHEN heart_rate < 55 THEN '#4FC3F7' " +
            "      WHEN heart_rate BETWEEN 55 AND 120 THEN '#66BB6A' " +
            "      ELSE '#FFB84D' " +
            "    END " +
            ") AS hr_stats " +
            "ORDER BY " +
            "  CASE category " +
            "    WHEN '心率偏低' THEN 1 " +
            "    WHEN '心率正常' THEN 2 " +
            "    WHEN '心率偏高' THEN 3 " +
            "  END")
    List<Map<String, Object>> getHeartRateDistributionNew(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取心率趋势数据（按天分组）
     * 返回: date(YYYY-MM-DD), avgHeartRate
     */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "CAST(AVG(CAST(heart_rate AS FLOAT)) AS INT) AS avgHeartRate " +
            "FROM v_health_record " +
            "WHERE heart_rate IS NOT NULL " +
            "AND heart_rate > 0 " +
            "AND record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<Map<String, Object>> getHeartRateTrend(@Param("days") int days);

    /**
     * 获取实时心率数据
     * 返回: userName, heartRate, recordTime
     */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "hr.heart_rate AS heartRate, " +
            "hr.record_time AS recordTime " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.heart_rate IS NOT NULL " +
            "AND hr.heart_rate > 0 " +
            "ORDER BY hr.record_time DESC")
    List<Map<String, Object>> getRealtimeData(@Param("limit") int limit);

    /**
     * 获取部门心率统计
     * 返回: deptName, avgHeartRate, lowCount, highCount, totalCount
     */
    @Select("SELECT " +
            "d.dept_name AS deptName, " +
            "CAST(AVG(CAST(hr.heart_rate AS FLOAT)) AS INT) AS avgHeartRate, " +
            "SUM(CASE WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS lowCount, " +
            "SUM(CASE WHEN hr.heart_rate > 120 THEN 1 ELSE 0 END) AS highCount, " +
            "SUM(CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN 1 ELSE 0 END) AS abnormalCount, " +
            "COUNT(*) AS totalCount " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.heart_rate IS NOT NULL " +
            "AND hr.heart_rate > 0 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY d.dept_name " +
            "ORDER BY avgHeartRate DESC")
    List<Map<String, Object>> getDepartmentStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取心率统计数据(保留原有方法,用于兼容)
     */
    @Select("SELECT " +
            "AVG(heart_rate) AS avgHeartRate, " +
            "MAX(heart_rate) AS maxHeartRate, " +
            "MIN(heart_rate) AS minHeartRate, " +
            "SUM(CASE WHEN heart_rate >= 60 AND heart_rate <= 100 THEN 1 ELSE 0 END) AS normalCount, " +
            "SUM(CASE WHEN heart_rate < 60 OR heart_rate > 100 THEN 1 ELSE 0 END) AS abnormalCount, " +
            "COUNT(*) AS totalCount " +
            "FROM v_health_record " +
            "WHERE heart_rate IS NOT NULL " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    Map<String, Object> getHeartRateStats();

    /**
     * 获取心率分布数据(保留原有方法,用于兼容)
     */
    @Select("SELECT " +
            "'<60' AS range, COUNT(*) AS count FROM v_health_record WHERE heart_rate < 60 AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "UNION ALL " +
            "SELECT '60-70', COUNT(*) FROM v_health_record WHERE heart_rate >= 60 AND heart_rate < 70 AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "UNION ALL " +
            "SELECT '70-80', COUNT(*) FROM v_health_record WHERE heart_rate >= 70 AND heart_rate < 80 AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "UNION ALL " +
            "SELECT '80-90', COUNT(*) FROM v_health_record WHERE heart_rate >= 80 AND heart_rate < 90 AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "UNION ALL " +
            "SELECT '90-100', COUNT(*) FROM v_health_record WHERE heart_rate >= 90 AND heart_rate <= 100 AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "UNION ALL " +
            "SELECT '>100', COUNT(*) FROM v_health_record WHERE heart_rate > 100 AND record_time >= DATEADD(DAY, -30, GETDATE())")
    List<Map<String, Object>> getHeartRateDistribution();

    /**
     * 获取异常心率记录（分页）
     */
    @Select("SELECT " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "ISNULL(d.dept_name, '') AS deptName, " +
            "hr.heart_rate AS heartRate, " +
            "CASE WHEN hr.heart_rate < 50 OR hr.heart_rate > 120 THEN 'danger' " +
            "     WHEN hr.heart_rate < 60 OR hr.heart_rate > 100 THEN 'warning' " +
            "     ELSE 'normal' END AS level, " +
            "hr.record_time AS recordTime " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE (hr.heart_rate < 60 OR hr.heart_rate > 100) " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY hr.record_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Map<String, Object>> getAbnormalRecords(@Param("offset") int offset, @Param("size") int size);

    /**
     * 获取异常记录总数
     */
    @Select("SELECT COUNT(*) " +
            "FROM v_health_record " +
            "WHERE (heart_rate < 60 OR heart_rate > 100) " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    int countAbnormalRecords();

    /**
     * 获取指定日期每小时平均心率
     */
    @Select("SELECT DATEPART(HOUR, record_time) AS hour, " +
            "CAST(AVG(CAST(heart_rate AS FLOAT)) AS INT) AS avgHeartRate " +
            "FROM v_health_record " +
            "WHERE heart_rate IS NOT NULL AND heart_rate > 0 " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY DATEPART(HOUR, record_time) " +
            "ORDER BY hour")
    List<Map<String, Object>> getHourlyStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 实时心率列表：每人取最新一条（近2小时），用窗口函数去重 */
    @Select("SELECT TOP (#{limit}) " +
            "userCode, userName, deptName, gender, age, jobType, heartRate, recordTime " +
            "FROM ( " +
            "  SELECT " +
            "    hr.user_code AS userCode, " +
            "    ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "    ISNULL(d.dept_name, '') AS deptName, " +
            "    CASE WHEN e.gender = 1 THEN '男' WHEN e.gender = 2 THEN '女' ELSE '' END AS gender, " +
            "    DATEDIFF(YEAR, e.birth_date, GETDATE()) AS age, " +
            "    ISNULL(jt.type_name, '') AS jobType, " +
            "    hr.heart_rate AS heartRate, " +
            "    hr.record_time AS recordTime, " +
            "    ROW_NUMBER() OVER (PARTITION BY hr.user_code ORDER BY hr.record_time DESC) AS rn " +
            "  FROM v_health_record hr " +
            "  LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "  LEFT JOIN department d ON e.dept_id = d.id " +
            "  LEFT JOIN job_type jt ON e.job_type_id = jt.id " +
            "  WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "  AND hr.record_time >= DATEADD(HOUR, -2, GETDATE()) " +
            ") t WHERE rn = 1 " +
            "ORDER BY recordTime DESC")
    List<Map<String, Object>> getRealtime(@Param("limit") int limit);

    /** 实时心率列表 — 直接查当月分区表，避免扫 v_health_record UNION ALL */
    @Select("SELECT TOP (#{limit}) " +
            "userCode, userName, deptName, gender, age, jobType, heartRate, recordTime " +
            "FROM ( " +
            "  SELECT " +
            "    hr.user_code AS userCode, " +
            "    ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "    ISNULL(d.dept_name, '') AS deptName, " +
            "    CASE WHEN e.gender = 1 THEN '男' WHEN e.gender = 2 THEN '女' ELSE '' END AS gender, " +
            "    DATEDIFF(YEAR, e.birth_date, GETDATE()) AS age, " +
            "    ISNULL(jt.type_name, '') AS jobType, " +
            "    hr.heart_rate AS heartRate, " +
            "    hr.record_time AS recordTime, " +
            "    ROW_NUMBER() OVER (PARTITION BY hr.user_code ORDER BY hr.record_time DESC) AS rn " +
            "  FROM ${tableSource} hr " +
            "  LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "  LEFT JOIN department d ON e.dept_id = d.id " +
            "  LEFT JOIN job_type jt ON e.job_type_id = jt.id " +
            "  WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "  AND hr.record_time >= DATEADD(HOUR, -2, GETDATE()) " +
            ") t WHERE rn = 1 " +
            "ORDER BY recordTime DESC")
    List<Map<String, Object>> getRealtimeDirect(@Param("tableSource") String tableSource,
                                                 @Param("limit") int limit);

    /** 心率趋势 — 直接查分区表，避免扫 UNION ALL 视图 */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "CAST(AVG(CAST(heart_rate AS FLOAT)) AS INT) AS avgHeartRate " +
            "FROM ${tableSource} " +
            "WHERE heart_rate IS NOT NULL " +
            "AND heart_rate > 0 " +
            "AND record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<Map<String, Object>> getHeartRateTrendDirect(@Param("tableSource") String tableSource,
                                                       @Param("days") int days);

    /** 部门心率统计 — 直接查分区表，避免扫 UNION ALL 视图 */
    @Select("SELECT " +
            "d.dept_name AS deptName, " +
            "CAST(AVG(CAST(hr.heart_rate AS FLOAT)) AS INT) AS avgHeartRate, " +
            "SUM(CASE WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS lowCount, " +
            "SUM(CASE WHEN hr.heart_rate > 120 THEN 1 ELSE 0 END) AS highCount, " +
            "SUM(CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN 1 ELSE 0 END) AS abnormalCount, " +
            "COUNT(*) AS totalCount " +
            "FROM ${tableSource} hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.heart_rate IS NOT NULL " +
            "AND hr.heart_rate > 0 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY d.dept_name " +
            "ORDER BY avgHeartRate DESC")
    List<Map<String, Object>> getDepartmentStatsDirect(@Param("tableSource") String tableSource,
                                                        @Param("startDate") String startDate,
                                                        @Param("endDate") String endDate);

    /** 部门异常人员下钻；统计周期与部门柱状图保持一致。 */
    @Select("SELECT " +
            "hr.user_code AS userCode, " +
            "ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "d.dept_name AS deptName, " +
            "COUNT(*) AS abnormalCount, " +
            "COUNT(DISTINCT CONVERT(VARCHAR(10), hr.record_time, 23)) AS anomalyDays, " +
            "SUM(CASE WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS lowCount, " +
            "SUM(CASE WHEN hr.heart_rate > 120 THEN 1 ELSE 0 END) AS highCount, " +
            "MIN(hr.heart_rate) AS minHeartRate, " +
            "MAX(hr.heart_rate) AS maxHeartRate, " +
            "MAX(hr.record_time) AS lastRecordTime " +
            "FROM ${tableSource} hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "AND (hr.heart_rate < 55 OR hr.heart_rate > 120) " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            "ORDER BY abnormalCount DESC, lastRecordTime DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Map<String, Object>> getDepartmentAbnormalUsersDirect(
            @Param("tableSource") String tableSource,
            @Param("deptName") String deptName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ( " +
            "SELECT hr.user_code " +
            "FROM ${tableSource} hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "AND (hr.heart_rate < 55 OR hr.heart_rate > 120) " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code " +
            ") AS department_users")
    int countDepartmentAbnormalUsersDirect(
            @Param("tableSource") String tableSource,
            @Param("deptName") String deptName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /** 顶部人数指标下钻；mode=abnormal 时仅返回周期内异常人员。 */
    @Select("SELECT userCode, userName, deptName, sampleCount, abnormalCount, anomalyDays, " +
            "lowCount, highCount, minHeartRate, maxHeartRate, riskCode, " +
            "CONVERT(VARCHAR(19), lastSampleTime, 120) AS lastSampleTime, " +
            "CONVERT(VARCHAR(19), lastRecordTime, 120) AS lastRecordTime " +
            "FROM ( " +
            "  SELECT hr.user_code AS userCode, " +
            "    ISNULL(e.emp_name, hr.user_code) AS userName, " +
            "    ISNULL(d.dept_name, '') AS deptName, " +
            "    COUNT(*) AS sampleCount, " +
            "    SUM(CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN 1 ELSE 0 END) AS abnormalCount, " +
            "    COUNT(DISTINCT CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN CONVERT(VARCHAR(10), hr.record_time, 23) END) AS anomalyDays, " +
            "    SUM(CASE WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS lowCount, " +
            "    SUM(CASE WHEN hr.heart_rate > 120 THEN 1 ELSE 0 END) AS highCount, " +
            "    MIN(hr.heart_rate) AS minHeartRate, MAX(hr.heart_rate) AS maxHeartRate, " +
            "    MAX(CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN hr.record_time END) AS lastRecordTime, " +
            "    MAX(hr.record_time) AS lastSampleTime, " +
            "    MAX(CASE WHEN hr.heart_rate > 150 THEN 3 WHEN hr.heart_rate > 120 THEN 2 WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS riskCode " +
            "  FROM ${tableSource} hr " +
            "  LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "  LEFT JOIN department d ON e.dept_id = d.id " +
            "  WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "  AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY hr.user_code, e.emp_name, d.dept_name " +
            ") AS period_users " +
            "WHERE ((#{riskCode} >= 0 AND riskCode = #{riskCode}) OR " +
            "(#{riskCode} < 0 AND (#{mode} <> 'abnormal' OR abnormalCount > 0))) " +
            "ORDER BY CASE WHEN #{riskCode} = 0 OR (#{riskCode} < 0 AND #{mode} <> 'abnormal') THEN sampleCount ELSE abnormalCount END DESC, lastSampleTime DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Map<String, Object>> getPeriodUsersDirect(
            @Param("tableSource") String tableSource,
            @Param("mode") String mode,
            @Param("riskCode") int riskCode,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ( " +
            "SELECT hr.user_code, " +
            "SUM(CASE WHEN hr.heart_rate < 55 OR hr.heart_rate > 120 THEN 1 ELSE 0 END) AS abnormalCount, " +
            "MAX(CASE WHEN hr.heart_rate > 150 THEN 3 WHEN hr.heart_rate > 120 THEN 2 WHEN hr.heart_rate < 55 THEN 1 ELSE 0 END) AS riskCode " +
            "FROM ${tableSource} hr " +
            "WHERE hr.heart_rate IS NOT NULL AND hr.heart_rate > 0 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code " +
            ") AS period_users WHERE ((#{riskCode} >= 0 AND riskCode = #{riskCode}) OR " +
            "(#{riskCode} < 0 AND (#{mode} <> 'abnormal' OR abnormalCount > 0)))")
    int countPeriodUsersDirect(
            @Param("tableSource") String tableSource,
            @Param("mode") String mode,
            @Param("riskCode") int riskCode,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /** 人员画像从心率分析下钻时使用的原始异常记录。 */
    @Select("SELECT " +
            "CONVERT(VARCHAR(19), record_time, 120) AS recordTime, " +
            "heart_rate AS heartRate, " +
            "CASE WHEN heart_rate < 55 THEN 'low' ELSE 'high' END AS direction, " +
            "CASE WHEN heart_rate > 150 THEN 'danger' ELSE 'warning' END AS level " +
            "FROM ${tableSource} " +
            "WHERE user_code = #{userCode} " +
            "AND heart_rate IS NOT NULL AND heart_rate > 0 " +
            "AND (heart_rate < 55 OR heart_rate > 120) " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "ORDER BY record_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Map<String, Object>> getUserAbnormalRecordsDirect(
            @Param("tableSource") String tableSource,
            @Param("userCode") String userCode,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ${tableSource} " +
            "WHERE user_code = #{userCode} " +
            "AND heart_rate IS NOT NULL AND heart_rate > 0 " +
            "AND (heart_rate < 55 OR heart_rate > 120) " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate}))")
    int countUserAbnormalRecordsDirect(
            @Param("tableSource") String tableSource,
            @Param("userCode") String userCode,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}
