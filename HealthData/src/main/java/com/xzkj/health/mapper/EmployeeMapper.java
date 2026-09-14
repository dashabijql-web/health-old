package com.xzkj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzkj.health.dto.employee.EmployeeCommandSearchRow;
import com.xzkj.health.model.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Select("SELECT e.id, e.emp_name AS empName, e.emp_code AS empCode, " +
            "e.gender, e.phone, e.dept_id AS deptId, e.job_type_id AS jobTypeId, " +
            "e.birth_date AS birthDate, e.hire_date AS hireDate, " +
            "e.height, e.weight, e.blood_type AS bloodType, e.status, " +
            "e.emergency_contact AS emergencyContact, " +
            "e.emergency_phone AS emergencyPhone, " +
            "e.medical_history AS medicalHistory, " +
            "d.dept_name AS deptName, j.type_name AS jobTypeName, " +
            "ISNULL(( " +
            "  SELECT CASE WHEN pts IS NULL OR pts <= 0 THEN 100 " +
            "              WHEN 100 - pts < 30 THEN 30 " +
            "              ELSE 100 - pts END " +
            "  FROM ( " +
            "    SELECT SUM(CASE warning_level WHEN '高危' THEN 6 WHEN '中危' THEN 3 ELSE 1 END) AS pts " +
            "    FROM v_warning_record w " +
            "    WHERE w.user_code = e.emp_code " +
            "    AND w.create_time >= DATEADD(day, -30, GETDATE()) " +
            "  ) t " +
            "), 100) AS healthScore " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "LEFT JOIN job_type j ON e.job_type_id = j.id " +
            "ORDER BY e.id DESC")
    List<Map<String, Object>> getEmployeeListWithDept();

    @Select({
            "<script>",
            "SELECT e.id AS employeeId, e.emp_code AS empCode, e.emp_name AS empName,",
            "d.dept_name AS deptName, j.type_name AS jobTypeName, e.phone,",
            "bound_device.imei, bound_device.deviceLastOnlineTime",
            "FROM employee e",
            "LEFT JOIN department d ON e.dept_id = d.id",
            "LEFT JOIN job_type j ON e.job_type_id = j.id",
            "OUTER APPLY (",
            "  SELECT TOP 1 dv.imei, CONVERT(varchar(19), dv.last_online_time, 120) AS deviceLastOnlineTime",
            "  FROM device_user du",
            "  INNER JOIN device dv ON dv.id = du.device_id",
            "  WHERE du.emp_id = e.id AND du.unbind_time IS NULL",
            "  ORDER BY du.bind_time DESC, du.id DESC",
            ") bound_device",
            "WHERE (e.status IS NULL OR e.status = 0)",
            "<if test='query != null and query != &quot;&quot;'>",
            "  AND (e.emp_name LIKE '%' + #{query} + '%'",
            "    OR e.emp_code LIKE '%' + #{query} + '%'",
            "    OR e.phone LIKE '%' + #{query} + '%'",
            "    OR bound_device.imei LIKE '%' + #{query} + '%')",
            "</if>",
            "ORDER BY CASE WHEN e.emp_code = #{query} THEN 0 WHEN e.emp_name = #{query} THEN 1 ELSE 2 END, e.emp_name, e.id",
            "OFFSET 0 ROWS FETCH NEXT #{limit} ROWS ONLY",
            "</script>"
    })
    List<EmployeeCommandSearchRow> searchForCommand(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN status = 0 OR status IS NULL THEN 1 ELSE 0 END) AS activeCount, " +
            "SUM(CASE WHEN gender = 1 THEN 1 ELSE 0 END) AS maleCount, " +
            "SUM(CASE WHEN gender = 2 THEN 1 ELSE 0 END) AS femaleCount " +
            "FROM employee")
    Map<String, Object> getEmployeeStats();

    @Select("SELECT COUNT(DISTINCT user_code) FROM v_health_record " +
            "WHERE CAST(record_time AS DATE) = CAST(GETDATE() AS DATE)")
    long getTodayOnlineCount();
}
