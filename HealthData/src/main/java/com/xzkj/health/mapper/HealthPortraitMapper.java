package com.xzkj.health.mapper;

import com.xzkj.health.dto.portrait.PortraitEmployeeRow;
import com.xzkj.health.dto.portrait.PortraitExerciseRow;
import com.xzkj.health.dto.portrait.PortraitHourlyHeartRateRow;
import com.xzkj.health.dto.portrait.PortraitTrendRow;
import com.xzkj.health.dto.portrait.PortraitVitalsRow;
import com.xzkj.health.dto.portrait.PortraitWarningRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HealthPortraitMapper {

    @Select("SELECT e.emp_name AS empName, e.emp_code AS empCode, " +
            "e.gender, e.phone, e.birth_date AS birthDate, e.hire_date AS hireDate, " +
            "e.height, e.weight, e.blood_type AS bloodType, e.status, " +
            "d.dept_name AS deptName, j.type_name AS jobTypeName " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "LEFT JOIN job_type j ON e.job_type_id = j.id " +
            "WHERE e.emp_code = #{empCode}")
    PortraitEmployeeRow getEmployeeDetail(@Param("empCode") String empCode);

    @Select("WITH Base AS ( " +
            "  SELECT heart_rate, blood_oxygen, temperature, blood_pressure_high, blood_pressure_low, pressure, steps, calories, record_time, " +
            "    ROW_NUMBER() OVER (ORDER BY record_time DESC) AS rn_any, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN heart_rate IS NOT NULL AND heart_rate > 0 THEN 1 END ORDER BY record_time DESC) AS rn_hr, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN blood_oxygen IS NOT NULL AND blood_oxygen > 0 THEN 1 END ORDER BY record_time DESC) AS rn_bo, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN temperature IS NOT NULL AND temperature > 0 THEN 1 END ORDER BY record_time DESC) AS rn_tp, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN blood_pressure_high IS NOT NULL AND blood_pressure_high > 0 THEN 1 END ORDER BY record_time DESC) AS rn_bph, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN blood_pressure_low IS NOT NULL AND blood_pressure_low > 0 THEN 1 END ORDER BY record_time DESC) AS rn_bpl, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN pressure IS NOT NULL THEN 1 END ORDER BY record_time DESC) AS rn_pr, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN steps IS NOT NULL THEN 1 END ORDER BY record_time DESC) AS rn_st, " +
            "    ROW_NUMBER() OVER (PARTITION BY CASE WHEN calories IS NOT NULL THEN 1 END ORDER BY record_time DESC) AS rn_cal " +
            "  FROM v_health_record " +
            "  WHERE user_code = #{empCode} " +
            "  AND record_time >= DATEADD(DAY, -30, GETDATE()) " +
            ") " +
            "SELECT " +
            "  MAX(CASE WHEN rn_hr  = 1 AND heart_rate IS NOT NULL AND heart_rate > 0 THEN heart_rate END) AS heartRate, " +
            "  MAX(CASE WHEN rn_bo  = 1 AND blood_oxygen IS NOT NULL AND blood_oxygen > 0 THEN blood_oxygen END) AS bloodOxygen, " +
            "  MAX(CASE WHEN rn_tp  = 1 AND temperature IS NOT NULL AND temperature > 0 THEN CAST(temperature AS FLOAT) / 10.0 END) AS temperature, " +
            "  MAX(CASE WHEN rn_bph = 1 AND blood_pressure_high IS NOT NULL AND blood_pressure_high > 0 THEN blood_pressure_high END) AS systolic, " +
            "  MAX(CASE WHEN rn_bpl = 1 AND blood_pressure_low  IS NOT NULL AND blood_pressure_low  > 0 THEN blood_pressure_low  END) AS diastolic, " +
            "  MAX(CASE WHEN rn_pr  = 1 AND pressure IS NOT NULL THEN pressure END) AS pressure, " +
            "  MAX(CASE WHEN rn_st  = 1 AND steps    IS NOT NULL THEN steps    END) AS steps, " +
            "  MAX(CASE WHEN rn_cal = 1 AND calories IS NOT NULL THEN calories END) AS calories, " +
            "  CONVERT(varchar(23), MAX(CASE WHEN heart_rate IS NOT NULL OR blood_oxygen IS NOT NULL " +
            "    OR temperature IS NOT NULL OR blood_pressure_high IS NOT NULL OR blood_pressure_low IS NOT NULL " +
            "    OR pressure IS NOT NULL THEN record_time END), 121) AS recordTime, " +
            "  DATEDIFF(SECOND, MAX(CASE WHEN heart_rate IS NOT NULL OR blood_oxygen IS NOT NULL " +
            "    OR temperature IS NOT NULL OR blood_pressure_high IS NOT NULL OR blood_pressure_low IS NOT NULL " +
            "    OR pressure IS NOT NULL THEN record_time END), GETDATE()) AS dataAgeSeconds " +
            ", CONVERT(varchar(23), MAX(record_time), 121) AS reportTime " +
            ", DATEDIFF(SECOND, MAX(record_time), GETDATE()) AS reportAgeSeconds " +
            ", CONVERT(varchar(23), MAX(CASE WHEN rn_hr = 1 AND heart_rate IS NOT NULL AND heart_rate > 0 THEN record_time END), 121) AS heartRateTime " +
            ", CONVERT(varchar(23), MAX(CASE WHEN rn_bo = 1 AND blood_oxygen IS NOT NULL AND blood_oxygen > 0 THEN record_time END), 121) AS bloodOxygenTime " +
            ", CONVERT(varchar(23), MAX(CASE WHEN rn_tp = 1 AND temperature IS NOT NULL AND temperature > 0 THEN record_time END), 121) AS temperatureTime " +
            ", CONVERT(varchar(23), MAX(CASE WHEN (rn_bph = 1 AND blood_pressure_high IS NOT NULL AND blood_pressure_high > 0) " +
            "    OR (rn_bpl = 1 AND blood_pressure_low IS NOT NULL AND blood_pressure_low > 0) THEN record_time END), 121) AS bloodPressureTime " +
            ", CONVERT(varchar(23), MAX(CASE WHEN rn_pr = 1 AND pressure IS NOT NULL THEN record_time END), 121) AS pressureTime " +
            "FROM Base")
    PortraitVitalsRow getLatestVitals(@Param("empCode") String empCode);

    @Select("SELECT TOP 1 " +
            "ISNULL(steps, 0) AS todaySteps, " +
            "ISNULL(calories, 0) AS todayCalories " +
            "FROM v_health_record " +
            "WHERE user_code = #{empCode} " +
            "AND record_time >= CONVERT(DATETIME, CONVERT(DATE, GETDATE())) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, CONVERT(DATE, GETDATE()))) " +
            "AND (steps IS NOT NULL OR calories IS NOT NULL) " +
            "ORDER BY record_time DESC")
    PortraitExerciseRow getTodayExercise(@Param("empCode") String empCode);

    @Select("SELECT CONVERT(VARCHAR(10), record_time, 120) AS date, " +
            "ROUND(AVG(CAST(heart_rate   AS FLOAT)), 0) AS avgHeartRate, " +
            "ROUND(AVG(CAST(blood_oxygen AS FLOAT)), 1) AS avgBloodOxygen " +
            "FROM v_health_record " +
            "WHERE user_code = #{empCode} " +
            "AND record_time >= DATEADD(DAY, -6, CAST(GETDATE() AS DATE)) " +
            "AND heart_rate IS NOT NULL " +
            "GROUP BY CONVERT(VARCHAR(10), record_time, 120) " +
            "ORDER BY date ASC")
    List<PortraitTrendRow> get7DayTrend(@Param("empCode") String empCode);

    @Select("SELECT TOP 50 " +
            "warning_type AS warningType, " +
            "indicator_name AS indicatorName, " +
            "indicator_value AS warningValue, " +
            "warning_level AS warningLevel, " +
            "create_time AS createTime " +
            "FROM v_warning_record " +
            "WHERE user_code = #{empCode} " +
            "AND create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "ORDER BY create_time DESC")
    List<PortraitWarningRow> get30DayWarnings(@Param("empCode") String empCode);

    @Select("SELECT DATEPART(HOUR, record_time) AS hour, " +
            "AVG(CAST(heart_rate AS FLOAT)) AS avgHr " +
            "FROM v_health_record " +
            "WHERE user_code = #{empCode} " +
            "AND record_time >= CONVERT(DATETIME, #{date}) " +
            "AND record_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{date})) " +
            "AND heart_rate IS NOT NULL AND heart_rate > 0 " +
            "GROUP BY DATEPART(HOUR, record_time) " +
            "ORDER BY hour ASC")
    List<PortraitHourlyHeartRateRow> getHourlyHeartRate(@Param("empCode") String empCode, @Param("date") String date);
}
