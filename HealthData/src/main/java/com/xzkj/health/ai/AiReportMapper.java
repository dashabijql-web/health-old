package com.xzkj.health.ai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiReportMapper {

    @Select("SELECT d.id, d.dept_name AS deptName, d.risk_level AS riskLevel, COUNT(e.id) AS empCount " +
            "FROM department d LEFT JOIN employee e ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "GROUP BY d.id, d.dept_name, d.risk_level")
    List<AiReportDepartmentInfoRow> selectDepartmentInfo(@Param("deptName") String deptName);

    @Select("SELECT AVG(CAST(h.heart_rate AS FLOAT)) AS avgHeartRate, " +
            "MIN(h.heart_rate) AS minHeartRate, MAX(h.heart_rate) AS maxHeartRate, " +
            "AVG(CAST(h.blood_oxygen AS FLOAT)) AS avgBloodOxygen, " +
            "MIN(h.blood_oxygen) AS minBloodOxygen, " +
            "AVG(CAST(h.blood_pressure_high AS FLOAT)) AS avgBpHigh, " +
            "AVG(CAST(h.blood_pressure_low AS FLOAT)) AS avgBpLow, " +
            "AVG(CAST(h.temperature AS FLOAT)) AS avgTemperature, " +
            "AVG(CAST(h.sleep_minutes AS FLOAT)) AS avgSleepMinutes, " +
            "AVG(CAST(h.steps AS FLOAT)) AS avgSteps, " +
            "AVG(CAST(h.calories AS FLOAT)) AS avgCalories, " +
            "AVG(CAST(h.pressure AS FLOAT)) AS avgPressure, " +
            "COUNT(DISTINCT h.user_code) AS monitoredEmpCount, COUNT(*) AS recordCount " +
            "FROM v_health_record h " +
            "JOIN employee e ON h.user_code = e.emp_code " +
            "JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND h.record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "AND h.heart_rate IS NOT NULL AND h.heart_rate > 0")
    AiReportHealthSummaryRow selectDepartmentHealthSummary(@Param("deptName") String deptName);

    @Select("SELECT w.warning_type AS warningType, w.warning_level AS warningLevel, COUNT(*) AS cnt " +
            "FROM v_warning_record w " +
            "JOIN employee e ON w.user_code = e.emp_code " +
            "JOIN department d ON e.dept_id = d.id " +
            "WHERE d.dept_name = #{deptName} " +
            "AND w.create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "GROUP BY w.warning_type, w.warning_level ORDER BY cnt DESC")
    List<AiReportWarningSummaryRow> selectDepartmentWarnings(@Param("deptName") String deptName);

    @Select("SELECT e.emp_name AS empName, e.emp_code AS empCode, e.gender, " +
            "CONVERT(VARCHAR(10), e.birth_date, 23) AS birthDate, " +
            "CONVERT(VARCHAR(10), e.hire_date, 23) AS hireDate, " +
            "d.dept_name AS deptName, j.type_name AS jobName " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "LEFT JOIN job_type j ON e.job_type_id = j.id " +
            "WHERE e.emp_code = #{empCode}")
    List<AiReportEmployeeInfoRow> selectEmployeeInfo(@Param("empCode") String empCode);

    @Select("SELECT AVG(CAST(heart_rate AS FLOAT)) AS avgHeartRate, " +
            "MIN(heart_rate) AS minHeartRate, MAX(heart_rate) AS maxHeartRate, " +
            "AVG(CAST(blood_oxygen AS FLOAT)) AS avgBloodOxygen, " +
            "MIN(blood_oxygen) AS minBloodOxygen, " +
            "AVG(CAST(blood_pressure_high AS FLOAT)) AS avgBpHigh, " +
            "MAX(blood_pressure_high) AS maxBpHigh, " +
            "AVG(CAST(blood_pressure_low AS FLOAT)) AS avgBpLow, " +
            "MAX(blood_pressure_low) AS maxBpLow, " +
            "AVG(CAST(temperature AS FLOAT)) AS avgTemperature, " +
            "MAX(temperature) AS maxTemperature, " +
            "AVG(CAST(sleep_minutes AS FLOAT)) AS avgSleepMinutes, " +
            "MIN(sleep_minutes) AS minSleepMinutes, " +
            "AVG(CAST(steps AS FLOAT)) AS avgSteps, " +
            "AVG(CAST(calories AS FLOAT)) AS avgCalories, " +
            "AVG(CAST(pressure AS FLOAT)) AS avgPressure, " +
            "MAX(pressure) AS maxPressure, " +
            "COUNT(*) AS recordCount " +
            "FROM v_health_record " +
            "WHERE user_code = #{empCode} " +
            "AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            "AND heart_rate IS NOT NULL AND heart_rate > 0")
    AiReportHealthSummaryRow selectEmployeeHealthSummary(@Param("empCode") String empCode);

    @Select("SELECT warning_type AS warningType, warning_level AS warningLevel, COUNT(*) AS cnt " +
            "FROM v_warning_record " +
            "WHERE user_code = #{empCode} " +
            "AND create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "GROUP BY warning_type, warning_level " +
            "ORDER BY cnt DESC")
    List<AiReportWarningSummaryRow> selectEmployeeWarnings(@Param("empCode") String empCode);

    @Select("SELECT dept_name FROM department WHERE dept_name IS NOT NULL ORDER BY dept_name")
    List<String> selectDepartmentNames();

    @Select("SELECT TOP 10 e.emp_name AS empName, d.dept_name AS deptName, " +
            "w.warning_type AS warningType, w.warning_level AS warningLevel, COUNT(*) AS cnt " +
            "FROM v_warning_record w " +
            "JOIN employee e ON w.user_code = e.emp_code " +
            "JOIN department d ON e.dept_id = d.id " +
            "WHERE w.create_time >= DATEADD(DAY, -1, GETDATE()) " +
            "AND w.is_handled = 0 " +
            "AND (w.warning_level = 'HIGH' OR w.warning_level = '高') " +
            "GROUP BY e.emp_name, d.dept_name, w.warning_type, w.warning_level " +
            "ORDER BY cnt DESC")
    List<AiReportHighRiskSummaryRow> selectDailyHighRiskSummaries();
}
