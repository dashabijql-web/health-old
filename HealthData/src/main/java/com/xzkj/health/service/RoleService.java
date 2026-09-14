package com.xzkj.health.service;

import com.xzkj.health.mapper.PermissionMapper;
import com.xzkj.health.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 角色管理Service
 */
@Slf4j
@Service
public class RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    /** 获取角色列表 */
    public Map<String, Object> getRoleList(String keyword, Integer status, int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = roleMapper.getRoleList(keyword, status, offset, size);
        int total = roleMapper.countRoles(keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 获取角色详细信息 */
    public Map<String, Object> getRoleDetail(Long id) {
        Map<String, Object> roleDetail = roleMapper.getRoleDetail(id);
        if (roleDetail != null) {
            List<Map<String, Object>> users = roleMapper.getRoleUsers(id);
            roleDetail.put("users", users);
        }
        return roleDetail;
    }

    /** 获取角色的用户列表 */
    public List<Map<String, Object>> getRoleUsers(Long roleId) {
        return roleMapper.getRoleUsers(roleId);
    }

    /** 获取角色统计数据 */
    public Map<String, Object> getRoleStats() {
        return roleMapper.getRoleStats();
    }

    /** 获取用户的角色列表 */
    public List<Map<String, Object>> getUserRoles(Long userId) {
        return roleMapper.getUserRoles(userId);
    }

    /** 获取所有可用角色（用于下拉选择） */
    public List<Map<String, Object>> getAllAvailableRoles() {
        return roleMapper.getAllAvailableRoles();
    }

    // ==================== CRUD ====================

    /** 新增角色 */
    @Transactional(rollbackFor = Exception.class)
    public void createRole(Map<String, Object> params) {
        String roleName = getString(params, "roleName");
        String roleCode = getString(params, "roleCode");
        if (roleName.isEmpty()) throw new IllegalArgumentException("角色名称不能为空");
        if (roleCode.isEmpty()) throw new IllegalArgumentException("角色编码不能为空");

        // 检查编码唯一性
        if (roleMapper.countByRoleCode(roleCode, 0L) > 0) {
            throw new IllegalArgumentException("角色编码已存在");
        }

        Map<String, Object> insertParams = new HashMap<>();
        insertParams.put("roleName",    roleName);
        insertParams.put("roleCode",    roleCode.toUpperCase());
        insertParams.put("description", getString(params, "description"));
        insertParams.put("status",      getInt(params, "status", 0));
        insertParams.put("createBy",    "admin");
        roleMapper.insertRole(insertParams);
    }

    /** 编辑角色 */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        if (id == null) throw new IllegalArgumentException("角色ID不能为空");
        String roleName = getString(params, "roleName");
        if (roleName.isEmpty()) throw new IllegalArgumentException("角色名称不能为空");

        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("id",          id);
        updateParams.put("roleName",    roleName);
        updateParams.put("description", getString(params, "description"));
        updateParams.put("status",      getInt(params, "status", 0));
        updateParams.put("updateBy",    "admin");
        roleMapper.updateRole(updateParams);
    }

    /** 删除角色（同时清除用户关联和权限关联） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        if (roleMapper.getRoleDetail(id) == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        roleMapper.deleteRoleUsers(id);
        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteRole(id);
    }

    // ==================== 权限分配 ====================

    /**
     * 获取权限树
     * 从 sys_permission 表查询所有节点，组装为树结构返回
     */
    public List<Map<String, Object>> getPermissionTree() {
        List<Map<String, Object>> allPermissions = permissionMapper.getAllPermissions();
        return buildTree(allPermissions, 0L);
    }

    /** 获取角色已分配的权限ID列表 */
    public List<Long> getRolePermissions(Long roleId) {
        return roleMapper.getRolePermissionIds(roleId);
    }

    /** 保存角色权限分配 */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 先清空旧权限
        roleMapper.deleteRolePermissions(roleId);
        // 逐条插入新权限
        if (permissionIds != null) {
            for (Long permId : permissionIds) {
                if (permId != null) {
                    roleMapper.insertRolePermission(roleId, permId);
                }
            }
        }
    }

    // ── 私有：将平铺列表组装为树 ──

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, Long parentId) {
        List<Map<String, Object>> children = new ArrayList<>();
        for (Map<String, Object> node : all) {
            Long pid = toLong(node.get("parentId"));
            if (Objects.equals(pid, parentId)) {
                List<Map<String, Object>> sub = buildTree(all, toLong(node.get("id")));
                if (!sub.isEmpty()) {
                    node.put("children", sub);
                }
                children.add(node);
            }
        }
        return children;
    }

    // ── 私有工具方法 ──

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return defaultVal; }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }
}
