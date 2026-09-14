package com.xzkj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzkj.health.dto.ai.AiHealthStatsRow;
import com.xzkj.health.dto.ai.AiWarningStatsRow;
import com.xzkj.health.model.AiHealthReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiHealthReportMapper extends BaseMapper<AiHealthReport> {

    /** 查询未过期的缓存报告 */
    @Select("SELECT TOP 1 * FROM ai_health_report " +
            "WHERE emp_code = #{empCode} AND expires_at > GETDATE() " +
            "ORDER BY generate_time DESC")
    AiHealthReport findValidCachedReport(@Param("empCode") String empCode);

    /** 删除员工的历史报告（插入前清理） */
    @Delete("DELETE FROM ai_health_report WHERE emp_code = #{empCode}")
    void deleteByEmpCode(@Param("empCode") String empCode);

    /** 按任意 key 查缓存（mine/dept 复用） */
    @Select("SELECT TOP 1 * FROM ai_health_report " +
            "WHERE emp_code = #{cacheKey} AND expires_at > GETDATE() " +
            "ORDER BY generate_time DESC")
    AiHealthReport findValidCachedReportByKey(@Param("cacheKey") String cacheKey);

    /** 全矿30天健康聚合 */
    @Select("SELECT COUNT(*) AS recordCount, " +
            "COUNT(DISTINCT user_code) AS empCount, " +
            "ROUND(AVG(CAST(heart_rate AS FLOAT)), 1) AS avgHeartRate, " +
            "MAX(heart_rate) AS maxHeartRate, MIN(heart_rate) AS minHeartRate, " +
            "ROUND(AVG(CAST(blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen, " +
            "MIN(blood_oxygen) AS minBloodOxygen, " +
            "ROUND(AVG(CAST(temperature AS FLOAT)) / 10.0, 1) AS avgTemperature, " +
            "ROUND(AVG(CAST(sleep_minutes AS FLOAT)) / 60.0, 1) AS avgSleepHours " +
            "FROM v_health_record " +
            "WHERE record_time >= DATEADD(DAY, -30, GETDATE())")
    AiHealthStatsRow getMineHealthStats();

    /** 全矿30天预警统计 */
    @Select("SELECT COUNT(*) AS totalWarnings, " +
            "SUM(CASE WHEN warning_level IN ('危急','高危') THEN 1 ELSE 0 END) AS highRiskCount, " +
            "SUM(CASE WHEN warning_level IN ('中危','中') THEN 1 ELSE 0 END) AS midRiskCount, " +
            "COUNT(DISTINCT user_code) AS affectedEmp " +
            "FROM v_warning_record " +
            "WHERE create_time >= DATEADD(DAY, -30, GETDATE())")
    AiWarningStatsRow getMineWarningStats();

    /** 部门30天健康聚合 */
    @Select("SELECT COUNT(*) AS recordCount, " +
            "COUNT(DISTINCT r.user_code) AS empCount, " +
            "ROUND(AVG(CAST(r.heart_rate AS FLOAT)), 1) AS avgHeartRate, " +
            "MAX(r.heart_rate) AS maxHeartRate, MIN(r.heart_rate) AS minHeartRate, " +
            "ROUND(AVG(CAST(r.blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen, " +
            "MIN(r.blood_oxygen) AS minBloodOxygen, " +
            "ROUND(AVG(CAST(r.temperature AS FLOAT)) / 10.0, 1) AS avgTemperature, " +
            "ROUND(AVG(CAST(r.sleep_minutes AS FLOAT)) / 60.0, 1) AS avgSleepHours " +
            "FROM v_health_record r " +
            "INNER JOIN employee e ON r.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND r.record_time >= DATEADD(DAY, -30, GETDATE())")
    AiHealthStatsRow getDeptHealthStats(@Param("deptName") String deptName);

    /** 部门30天预警统计 */
    @Select("SELECT COUNT(*) AS totalWarnings, " +
            "SUM(CASE WHEN w.warning_level IN ('危急','高危') THEN 1 ELSE 0 END) AS highRiskCount, " +
            "SUM(CASE WHEN w.warning_level IN ('中危','中') THEN 1 ELSE 0 END) AS midRiskCount " +
            "FROM v_warning_record w " +
            "INNER JOIN employee e ON w.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND w.create_time >= DATEADD(DAY, -30, GETDATE())")
    AiWarningStatsRow getDeptWarningStats(@Param("deptName") String deptName);

    /** 30天健康数据聚合（用于拼装AI prompt） */
    @Select("SELECT " +
            "COUNT(*) AS recordCount, " +
            "ROUND(AVG(CAST(heart_rate AS FLOAT)), 1) AS avgHeartRate, " +
            "MAX(heart_rate) AS maxHeartRate, " +
            "MIN(heart_rate) AS minHeartRate, " +
            "ROUND(AVG(CAST(blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen, " +
            "MIN(blood_oxygen) AS minBloodOxygen, " +
            "ROUND(AVG(CAST(temperature AS FLOAT)) / 10.0, 1) AS avgTemperature, " +
            "ROUND(AVG(CAST(sleep_minutes AS FLOAT)) / 60.0, 1) AS avgSleepHours " +
            "FROM v_health_record " +
            "WHERE user_code = #{empCode} " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE())")
    AiHealthStatsRow get30DayHealthStats(@Param("empCode") String empCode);

    /** 30天预警统计 */
    @Select("SELECT " +
            "COUNT(*) AS totalWarnings, " +
            "SUM(CASE WHEN warning_level IN ('危急','高危') THEN 1 ELSE 0 END) AS highRiskCount " +
            "FROM v_warning_record " +
            "WHERE user_code = #{empCode} " +
            "AND create_time >= DATEADD(DAY, -30, GETDATE())")
    AiWarningStatsRow get30DayWarningStats(@Param("empCode") String empCode);
}
