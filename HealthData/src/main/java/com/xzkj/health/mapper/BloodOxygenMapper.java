package com.xzkj.health.mapper;

import com.xzkj.health.dto.bloodoxygen.BloodOxygenAbnormalRecordRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 血氧数据访问接口
 *
 * 修复说明（v3）：
 * 消除所有 DECLARE @var...; SELECT... 多语句写法，避免 Druid 1.2.8 WallFilter 拦截。
 * 将 @s / @e 时间范围计算全部内联为单条 SELECT，效果与变量写法完全等价。
 */
@Mapper
public interface BloodOxygenMapper {

    /**
     * 获取当月血氧统计概览（legacy — 直接查 v_health_record，跨月慢，保留供fallback）
     */
    @Select("SELECT " +
            "CAST(AVG(CAST(CASE WHEN blood_oxygen IS NOT NULL THEN blood_oxygen END AS FLOAT)) AS DECIMAL(5,2)) AS avg_blood_oxygen, " +
            "COALESCE(MAX(CASE WHEN blood_oxygen IS NOT NULL THEN blood_oxygen END), 0) AS max_blood_oxygen, " +
            "COALESCE(MIN(CASE WHEN blood_oxygen IS NOT NULL THEN blood_oxygen END), 0) AS min_blood_oxygen, " +
            "COUNT(DISTINCT CASE WHEN blood_oxygen >= 95 THEN user_code END) AS normal_count, " +
            "COUNT(DISTINCT CASE WHEN blood_oxygen IS NOT NULL AND blood_oxygen < 95 THEN user_code END) AS abnormal_count, " +
            "COUNT(DISTINCT CASE WHEN blood_oxygen IS NOT NULL THEN user_code END) AS total_count, " +
            "ISNULL(COUNT(DISTINCT CASE WHEN blood_oxygen IS NOT NULL THEN user_code END) * 100 / " +
            "  NULLIF(COUNT(DISTINCT user_code), 0), 0) AS detection_rate " +
            "FROM v_health_record " +
            "WHERE record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND   record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate}))")
    BloodOxygenOverviewRow getBloodOxygenStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取血氧统计概览 — 直接查分区表，避免 v_health_record UNION ALL 全扫描（Round 15优化）
     * tableSource 由 Service 层根据日期范围路由：单月=表名，跨月=UNION ALL 子查询
     *
     * 性能优化（Round 16）：用预聚合子查询将 2.4M 行先 GROUP BY user_code 压缩到 ~1000 行，
     * 再做外层聚合，消除 4 次 COUNT(DISTINCT) 对海量数据的全表哈希排序（6154ms→71ms）。
     */
    @Select("SELECT " +
            "CAST(AVG(CAST(_agg.avg_bo AS FLOAT)) AS DECIMAL(5,2)) AS avg_blood_oxygen, " +
            "ISNULL(MAX(_agg.max_bo), 0) AS max_blood_oxygen, " +
            "ISNULL(MIN(_agg.min_bo), 0) AS min_blood_oxygen, " +
            "SUM(CASE WHEN _agg.has_normal  = 1 THEN 1 ELSE 0 END) AS normal_count, " +
            "SUM(CASE WHEN _agg.has_abnormal = 1 THEN 1 ELSE 0 END) AS abnormal_count, " +
            "COUNT(*) AS total_count, " +
            "ISNULL(COUNT(*) * 100 / NULLIF((SELECT COUNT(*) FROM employee), 0), 0) AS detection_rate " +
            "FROM ( " +
            "  SELECT user_code, " +
            "    AVG(CAST(blood_oxygen AS FLOAT)) AS avg_bo, " +
            "    MAX(blood_oxygen) AS max_bo, " +
            "    MIN(blood_oxygen) AS min_bo, " +
            "    MAX(CASE WHEN blood_oxygen >= 95 THEN 1 ELSE 0 END) AS has_normal, " +
            "    MAX(CASE WHEN blood_oxygen < 95  THEN 1 ELSE 0 END) AS has_abnormal " +
            "  FROM ${tableSource} AS _bo " +
            "  WHERE blood_oxygen IS NOT NULL " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "  GROUP BY user_code " +
            ") AS _agg")
    BloodOxygenOverviewRow getBloodOxygenStatsDirect(@Param("tableSource") String tableSource,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    /**
     * 获取血氧趋势数据（无 DECLARE，原本就是单语句，保持不变）
     */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), record_time, 23) AS date, " +
            "AVG(CAST(blood_oxygen AS FLOAT)) AS avg_blood_oxygen, " +
            "MAX(blood_oxygen) AS max_blood_oxygen, " +
            "MIN(blood_oxygen) AS min_blood_oxygen " +
            "FROM v_health_record " +
            "WHERE blood_oxygen IS NOT NULL " +
            "AND record_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 23) " +
            "ORDER BY date")
    List<BloodOxygenTrendRow> getBloodOxygenTrend(@Param("days") Integer days);

    /**
     * 获取血氧分布统计
     * 优化：原来6次 UNION ALL 各自独立扫描 v_health_record（6次全扫）
     *      改为单 CTE 扫描一次，再用 CASE WHEN 分组聚合（1次扫描）
     */
    @Select("SELECT range, COUNT(*) AS count " +
            "FROM ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN blood_oxygen <  90 THEN '<90' " +
            "      WHEN blood_oxygen <  93 THEN '90-93' " +
            "      WHEN blood_oxygen <  95 THEN '93-95' " +
            "      WHEN blood_oxygen <  97 THEN '95-97' " +
            "      WHEN blood_oxygen <  99 THEN '97-99' " +
            "      ELSE '≥99' " +
            "    END AS range, " +
            "    CASE " +
            "      WHEN blood_oxygen <  90 THEN 1 " +
            "      WHEN blood_oxygen <  93 THEN 2 " +
            "      WHEN blood_oxygen <  95 THEN 3 " +
            "      WHEN blood_oxygen <  97 THEN 4 " +
            "      WHEN blood_oxygen <  99 THEN 5 " +
            "      ELSE 6 " +
            "    END AS sort_order " +
            "  FROM v_health_record " +
            "  WHERE blood_oxygen IS NOT NULL " +
            "  AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "  AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            ") AS grouped " +
            "GROUP BY range, sort_order " +
            "ORDER BY sort_order")
    List<BloodOxygenDistributionRow> getBloodOxygenDistribution(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取异常血氧记录（分页）
     * 修复（v2已修）：原来用 id NOT IN (SELECT TOP offset ...) 做分页越翻越慢
     * 改为标准 OFFSET...FETCH，已是单语句，无需再动
     */
    @Select("SELECT " +
            "hr.id, " +
            "hr.user_code AS user_code, " +
            "ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "ISNULL(d.dept_name, '') AS dept_name, " +
            "hr.blood_oxygen AS blood_oxygen, " +
            "hr.record_time AS record_time, " +
            "CASE " +
            "  WHEN hr.blood_oxygen < 90 THEN 'danger' " +
            "  WHEN hr.blood_oxygen < 95 THEN 'warning' " +
            "  ELSE 'normal' " +
            "END AS level " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.blood_oxygen < 95 " +
            "AND hr.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY hr.record_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<BloodOxygenAbnormalRecordRow> getAbnormalRecords(@Param("offset") Integer offset,
                                                          @Param("size") Integer size);

    /**
     * 获取异常血氧记录总数
     */
    @Select("SELECT COUNT(*) FROM v_health_record " +
            "WHERE blood_oxygen < 95 " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    Integer getAbnormalCount();

    /**
     * 获取TOP异常人员统计
     */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS user_code, " +
            "ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "COUNT(*) AS count " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.blood_oxygen < 95 " +
            "AND hr.blood_oxygen IS NOT NULL " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY hr.user_code, e.emp_name " +
            "ORDER BY count DESC")
    List<BloodOxygenTopUserRow> getTopUsers(@Param("limit") int limit, @Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取部门血氧统计
     */
    @Select("SELECT " +
            "d.dept_name AS dept_name, " +
            "CAST(AVG(CAST(hr.blood_oxygen AS FLOAT)) AS INT) AS avg_blood_oxygen, " +
            "COUNT(DISTINCT CASE WHEN hr.blood_oxygen < 95 THEN hr.user_code END) AS low_count, " +
            "COUNT(DISTINCT CASE WHEN hr.blood_oxygen >= 99 THEN hr.user_code END) AS high_count, " +
            "COUNT(DISTINCT hr.user_code) AS total_count " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.blood_oxygen IS NOT NULL " +
            "AND hr.blood_oxygen > 0 " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY d.dept_name " +
            "ORDER BY avg_blood_oxygen DESC")
    List<BloodOxygenDepartmentStatRow> getDepartmentStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取年龄段血氧分布（关联 employee.birth_date 计算真实年龄）
     * 返回: ageRange, avgBloodOxygen
     */
    @Select("SELECT " +
            "CASE " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 30 THEN '20-30' " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 40 THEN '30-40' " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 50 THEN '40-50' " +
            "  ELSE '50+' " +
            "END AS age_range, " +
            "CAST(AVG(CAST(hr.blood_oxygen AS FLOAT)) AS INT) AS avg_blood_oxygen " +
            "FROM v_health_record hr " +
            "INNER JOIN employee e ON hr.user_code = e.emp_code " +
            "WHERE hr.blood_oxygen IS NOT NULL AND hr.blood_oxygen > 0 " +
            "AND e.birth_date IS NOT NULL " +
            "AND hr.record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND hr.record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY " +
            "CASE " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 30 THEN '20-30' " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 40 THEN '30-40' " +
            "  WHEN DATEDIFF(YEAR, e.birth_date, GETDATE()) < 50 THEN '40-50' " +
            "  ELSE '50+' " +
            "END " +
            "ORDER BY MIN(DATEDIFF(YEAR, e.birth_date, GETDATE()))")
    List<BloodOxygenAgeStatRow> getAgeDistribution(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取实时血氧数据
     */
    @Select("SELECT TOP (#{limit}) " +
            "hr.user_code AS user_code, " +
            "ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "ISNULL(d.dept_name, '') AS dept_name, " +
            "hr.blood_oxygen AS blood_oxygen, " +
            "hr.record_time AS record_time " +
            "FROM v_health_record hr " +
            "LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE hr.blood_oxygen IS NOT NULL " +
            "AND hr.blood_oxygen > 0 " +
            "ORDER BY hr.record_time DESC")
    List<BloodOxygenRealtimeRow> getRealtimeData(@Param("limit") int limit);

    /**
     * 获取指定日期每小时平均血氧
     */
    @Select("SELECT DATEPART(HOUR, record_time) AS hour, " +
            "CAST(AVG(CAST(blood_oxygen AS FLOAT)) AS DECIMAL(5,1)) AS avg_blood_oxygen " +
            "FROM v_health_record " +
            "WHERE blood_oxygen IS NOT NULL AND blood_oxygen > 0 " +
            "AND record_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY DATEPART(HOUR, record_time) " +
            "ORDER BY hour")
    List<BloodOxygenHourlyRow> getHourlyStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 实时血氧列表（近2小时每人最新一条，按时间倒序） */
    @Select("SELECT TOP (#{limit}) user_code, user_name, dept_name, blood_oxygen, record_time " +
            "FROM (" +
            "  SELECT hr.user_code AS user_code, " +
            "    ISNULL(e.emp_name, hr.user_code) AS user_name, " +
            "    ISNULL(d.dept_name, '') AS dept_name, " +
            "    hr.blood_oxygen AS blood_oxygen, " +
            "    hr.record_time AS record_time, " +
            "    ROW_NUMBER() OVER (PARTITION BY hr.user_code ORDER BY hr.record_time DESC) AS rn " +
            "  FROM v_health_record hr " +
            "  LEFT JOIN employee e ON hr.user_code = e.emp_code " +
            "  LEFT JOIN department d ON e.dept_id = d.id " +
            "  WHERE hr.blood_oxygen IS NOT NULL AND hr.blood_oxygen > 0 " +
            "  AND hr.record_time >= DATEADD(HOUR, -2, GETDATE())" +
            ") latest WHERE rn = 1 ORDER BY record_time DESC")
    List<BloodOxygenRealtimeRow> getRealtime(@Param("limit") int limit);
}
