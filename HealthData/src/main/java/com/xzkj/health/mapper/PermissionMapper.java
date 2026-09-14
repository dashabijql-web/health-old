package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 权限Mapper
 *
 * 对应数据库表：sys_permission
 * 建表SQL（SQL Server）：
 * -------------------------------------------------------
 * CREATE TABLE sys_permission (
 *     id          BIGINT IDENTITY(1,1) PRIMARY KEY,
 *     parent_id   BIGINT       NOT NULL DEFAULT 0,
 *     name        NVARCHAR(100) NOT NULL,
 *     type        NVARCHAR(20)  NOT NULL DEFAULT 'menu',  -- module/menu/button
 *     perm_code   NVARCHAR(100) NULL,
 *     icon        NVARCHAR(50)  NULL,
 *     sort_order  INT           NOT NULL DEFAULT 0,
 *     status      INT           NOT NULL DEFAULT 0,       -- 0启用 1禁用
 *     create_time DATETIME      NOT NULL DEFAULT GETDATE()
 * );
 *
 * 对应数据库表：sys_role_permission
 * CREATE TABLE sys_role_permission (
 *     id            BIGINT IDENTITY(1,1) PRIMARY KEY,
 *     role_id       BIGINT NOT NULL,
 *     permission_id BIGINT NOT NULL,
 *     CONSTRAINT UQ_rp UNIQUE (role_id, permission_id)
 * );
 * -------------------------------------------------------
 */
@Mapper
public interface PermissionMapper {

    /**
     * 查询所有启用的权限节点（平铺，由Service组装树结构）
     */
    @Select("SELECT id, parent_id AS parentId, name, type, perm_code AS permCode, " +
            "icon, sort_order AS sortOrder " +
            "FROM sys_permission " +
            "WHERE status = 0 " +
            "ORDER BY parent_id, sort_order, id")
    List<Map<String, Object>> getAllPermissions();

    /**
     * 查询用户拥有的菜单权限码（module + menu 类型）
     * 路径：用户 → 角色 → 角色权限 → 权限节点
     * 注意：只返回启用状态的角色所拥有的权限
     */
    @Select("SELECT DISTINCT p.perm_code " +
            "FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} " +
            "AND p.status = 0 " +
            "AND r.status = 0 " +
            "AND p.perm_code IS NOT NULL " +
            "AND p.type IN ('module', 'menu')")
    List<String> getUserMenuPermCodes(@Param("userId") Long userId);

    /**
     * 查询用户拥有的按钮权限码（button 类型）
     * 注意：只返回启用状态的角色所拥有的权限
     */
    @Select("SELECT DISTINCT p.perm_code " +
            "FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} " +
            "AND p.status = 0 " +
            "AND r.status = 0 " +
            "AND p.perm_code IS NOT NULL " +
            "AND p.type = 'button'")
    List<String> getUserButtonPermCodes(@Param("userId") Long userId);
}
