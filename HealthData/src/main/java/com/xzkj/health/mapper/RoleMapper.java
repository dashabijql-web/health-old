package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理Mapper
 */
@Mapper
public interface RoleMapper {

    /** 获取角色列表（带筛选和分页） */
    @Select("<script>" +
            "SELECT r.id, r.role_name AS roleName, r.role_code AS roleCode, r.status, " +
            "r.description, r.create_time AS createTime, r.create_by AS createBy, " +
            "r.update_time AS updateTime, r.update_by AS updateBy, " +
            "(SELECT COUNT(*) FROM sys_user_role WHERE role_id = r.id) AS userCount " +
            "FROM sys_role r " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (r.role_name LIKE '%' + #{keyword} + '%' " +
            "     OR r.role_code LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "<if test='status != null'>AND r.status = #{status} </if>" +
            "</where>" +
            "ORDER BY r.create_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY" +
            "</script>")
    List<Map<String, Object>> getRoleList(@Param("keyword") String keyword,
                                          @Param("status") Integer status,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /** 获取角色总数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_role r " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (r.role_name LIKE '%' + #{keyword} + '%' " +
            "     OR r.role_code LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "<if test='status != null'>AND r.status = #{status} </if>" +
            "</where>" +
            "</script>")
    int countRoles(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 获取角色详细信息 */
    @Select("SELECT r.id, r.role_name AS roleName, r.role_code AS roleCode, r.status, " +
            "r.description, r.create_time AS createTime, r.create_by AS createBy, " +
            "r.update_time AS updateTime, r.update_by AS updateBy, " +
            "(SELECT COUNT(*) FROM sys_user_role WHERE role_id = r.id) AS userCount " +
            "FROM sys_role r WHERE r.id = #{id}")
    Map<String, Object> getRoleDetail(@Param("id") Long id);

    /** 查询角色编码是否已存在（排除自身） */
    @Select("SELECT COUNT(*) FROM sys_role WHERE role_code = #{roleCode} AND id != #{excludeId}")
    int countByRoleCode(@Param("roleCode") String roleCode, @Param("excludeId") Long excludeId);

    /** 获取角色的用户列表 */
    @Select("SELECT u.id, u.username AS userCode, u.real_name AS realName, " +
            "u.phone, u.email, d.dept_name AS deptName, u.status " +
            "FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "LEFT JOIN sys_dept d ON u.dept_id = d.id " +
            "WHERE ur.role_id = #{roleId} ORDER BY u.create_time DESC")
    List<Map<String, Object>> getRoleUsers(@Param("roleId") Long roleId);

    /** 获取角色统计数据 */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM sys_role) AS totalRoles, " +
            "(SELECT COUNT(*) FROM sys_role WHERE status = 0) AS activeRoles, " +
            "(SELECT COUNT(*) FROM sys_role WHERE status = 1) AS inactiveRoles")
    Map<String, Object> getRoleStats();

    /** 获取用户的角色列表 */
    @Select("SELECT r.id, r.role_name AS roleName, r.role_code AS roleCode, r.description " +
            "FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 0")
    List<Map<String, Object>> getUserRoles(@Param("userId") Long userId);

    /** 获取所有可用角色（用于下拉选择） */
    @Select("SELECT id, role_name AS roleName, role_code AS roleCode, description " +
            "FROM sys_role WHERE status = 0 ORDER BY create_time DESC")
    List<Map<String, Object>> getAllAvailableRoles();

    // ==================== CRUD ====================

    /** 新增角色 */
    @Insert("INSERT INTO sys_role (role_name, role_code, description, status, create_time, create_by) " +
            "VALUES (#{roleName}, #{roleCode}, #{description}, #{status}, GETDATE(), #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRole(Map<String, Object> params);

    /** 编辑角色 */
    @Update("UPDATE sys_role SET role_name = #{roleName}, description = #{description}, " +
            "status = #{status}, update_time = GETDATE(), update_by = #{updateBy} " +
            "WHERE id = #{id}")
    int updateRole(Map<String, Object> params);

    /** 删除角色 */
    @Delete("DELETE FROM sys_role WHERE id = #{id}")
    int deleteRole(@Param("id") Long id);

    /** 删除角色与用户的所有关联 */
    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteRoleUsers(@Param("roleId") Long roleId);

    // ==================== 权限分配 ====================

    /** 查询角色已分配的权限ID列表 */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> getRolePermissionIds(@Param("roleId") Long roleId);

    /** 删除角色所有权限 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    /** 批量插入角色权限 */
    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
