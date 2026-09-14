package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 用户列表Mapper
 */
@Mapper
public interface UserListMapper {

    /** 获取用户列表（带筛选和分页） */
    @Select("<script>" +
            "SELECT u.id, u.username AS userCode, u.real_name AS realName, u.nickname, " +
            "u.phone, u.email, u.status, u.gender, d.dept_name AS deptName, " +
            "u.create_time AS createTime, u.update_time AS updateTime, " +
            "uos.is_online AS isOnline, uos.last_update AS lastOnlineTime " +
            "FROM sys_user u " +
            "LEFT JOIN department d ON u.dept_id = d.id " +
            "LEFT JOIN user_online_status uos ON u.username = uos.user_code " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.username LIKE '%' + #{keyword} + '%' " +
            "     OR u.real_name LIKE '%' + #{keyword} + '%' " +
            "     OR u.phone LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "<if test='status != null'>AND u.status = #{status} </if>" +
            "<if test='deptId != null'>AND u.dept_id = #{deptId} </if>" +
            "</where>" +
            "ORDER BY u.create_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY" +
            "</script>")
    List<Map<String, Object>> getUserList(@Param("keyword") String keyword,
                                          @Param("status") Integer status,
                                          @Param("deptId") Long deptId,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /** 获取用户总数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user u " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.username LIKE '%' + #{keyword} + '%' " +
            "     OR u.real_name LIKE '%' + #{keyword} + '%' " +
            "     OR u.phone LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "<if test='status != null'>AND u.status = #{status} </if>" +
            "<if test='deptId != null'>AND u.dept_id = #{deptId} </if>" +
            "</where>" +
            "</script>")
    int countUsers(@Param("keyword") String keyword,
                   @Param("status") Integer status,
                   @Param("deptId") Long deptId);

    /** 获取系统用户详细信息（用于管理员用户详情页） */
    @Select("SELECT u.id, u.username AS userCode, u.real_name AS realName, u.nickname, " +
            "u.avatar, u.phone, u.email, u.status, u.gender, " +
            "u.dept_id AS deptId, d.dept_name AS deptName, " +
            "u.create_time AS createTime, u.create_by AS createBy, " +
            "u.update_time AS updateTime, u.update_by AS updateBy, u.remark, " +
            "uos.is_online AS isOnline, uos.last_update AS lastOnlineTime " +
            "FROM sys_user u " +
            "LEFT JOIN department d ON u.dept_id = d.id " +
            "LEFT JOIN user_online_status uos ON u.username = uos.user_code " +
            "WHERE u.id = #{id}")
    Map<String, Object> getUserDetail(@Param("id") Long id);

    /** 获取员工详细信息（用于设备数据写入时关联员工） */
    @Select("SELECT e.id, e.emp_code AS userCode, e.emp_name AS realName, " +
            "e.phone, d.dept_name AS deptName, j.risk_level AS riskLevel " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "LEFT JOIN job_type j ON e.job_type_id = j.id " +
            "WHERE e.id = #{id}")
    Map<String, Object> getEmpDetail(@Param("id") Long id);

    /** 按用户名查询（用于判断重复） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    /** 按手机号查询（排除自身） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE phone = #{phone} AND id != #{excludeId}")
    int countByPhone(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    /** 获取用户统计数据 */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM sys_user) AS totalUsers, " +
            "(SELECT COUNT(*) FROM sys_user WHERE status = 0) AS activeUsers, " +
            "(SELECT COUNT(*) FROM sys_user WHERE status = 1) AS inactiveUsers, " +
            "(SELECT COUNT(*) FROM user_online_status WHERE is_online = 1) AS onlineUsers, " +
            "CASE WHEN (SELECT COUNT(*) FROM sys_user WHERE status = 0) > 0 " +
            "     THEN (SELECT COUNT(*) FROM user_online_status WHERE is_online = 1) * 100 " +
            "          / (SELECT COUNT(*) FROM sys_user WHERE status = 0) " +
            "     ELSE 0 END AS onlineRate")
    Map<String, Object> getUserStats();

    /** 获取用户健康记录统计 */
    @Select("SELECT user_code AS userCode, COUNT(*) AS recordCount, " +
            "MAX(record_time) AS lastRecordTime, " +
            "AVG(CAST(heart_rate AS FLOAT)) AS avgHeartRate, " +
            "AVG(CAST(blood_oxygen AS FLOAT)) AS avgBloodOxygen, " +
            "AVG(CAST(sleep_minutes AS FLOAT)) AS avgSleepMinutes " +
            "FROM v_health_record " +
            "WHERE user_code = #{userCode} AND DATEDIFF(DAY, record_time, GETDATE()) < 30 " +
            "GROUP BY user_code")
    Map<String, Object> getUserHealthStats(@Param("userCode") String userCode);

    // ==================== CRUD ====================

    /** 新增用户 */
    @Insert("INSERT INTO sys_user " +
            "(username, password, real_name, nickname, phone, email, gender, dept_id, status, remark, create_time, create_by) " +
            "VALUES " +
            "(#{username}, #{password}, #{realName}, #{nickname}, #{phone}, #{email}, #{gender}, #{deptId}, #{status}, #{remark}, GETDATE(), #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertUser(Map<String, Object> params);

    /** 编辑用户（不含密码） */
    @Update("<script>" +
            "UPDATE sys_user SET " +
            "real_name = #{realName}, nickname = #{nickname}, phone = #{phone}, " +
            "email = #{email}, gender = #{gender}, dept_id = #{deptId}, " +
            "status = #{status}, remark = #{remark}, " +
            "update_time = GETDATE(), update_by = #{updateBy} " +
            "<if test='password != null and password != \"\"'>, password = #{password} </if>" +
            "WHERE id = #{id}" +
            "</script>")
    int updateUser(Map<String, Object> params);

    /** 仅更新用户状态 */
    @Update("UPDATE sys_user SET status = #{status}, update_time = GETDATE() WHERE id = #{id}")
    int updateUserStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 删除用户 */
    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteUser(@Param("id") Long id);

    /** 查询用户已关联的角色ID列表 */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> getUserRoleIds(@Param("userId") Long userId);

    /** 插入用户角色关联 */
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /** 删除用户所有角色关联 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    /**
     * 搜索员工（用于设备绑定自动补全）
     * 返回：id, realName, deptName, phone
     * 排除已绑定设备的员工（一个员工只能绑定一个手表）
     */
    @Select("SELECT TOP 20 " +
            "e.id, " +
            "e.emp_name AS realName, " +
            "d.dept_name AS deptName, " +
            "e.phone " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE (e.status IS NULL OR e.status = 0) " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM device_user du " +
            "    WHERE du.emp_id = e.id AND du.unbind_time IS NULL" +
            ") " +
            "AND (e.emp_name LIKE '%' + #{query} + '%' " +
            "     OR e.phone LIKE '%' + #{query} + '%' " +
            "     OR e.emp_code LIKE '%' + #{query} + '%') " +
            "ORDER BY " +
            "CASE WHEN e.emp_name LIKE #{query} + '%' THEN 1 " +
            "     WHEN e.emp_name LIKE '%' + #{query} + '%' THEN 2 " +
            "     ELSE 3 END, " +
            "e.emp_name")
    List<Map<String, Object>> searchUsers(@Param("query") String query);
}
