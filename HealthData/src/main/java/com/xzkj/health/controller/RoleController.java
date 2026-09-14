package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 角色管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    /** 获取角色列表 */
    @GetMapping("/list")
    public Result<Map<String, Object>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            size = DateParamUtil.clampSize(size);
            return Result.ok("获取成功", roleService.getRoleList(keyword, status, page, size));
        } catch (Exception e) {
            log.error("获取角色列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取角色详细信息 */
    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        try {
            Map<String, Object> data = roleService.getRoleDetail(id);
            return data != null ? Result.ok("获取成功", data) : Result.error("角色不存在");
        } catch (Exception e) {
            log.error("获取角色详细信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取角色的用户列表 */
    @GetMapping("/users/{roleId}")
    public Result<List<Map<String, Object>>> getRoleUsers(@PathVariable Long roleId) {
        try {
            return Result.ok("获取成功", roleService.getRoleUsers(roleId));
        } catch (Exception e) {
            log.error("获取角色用户列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取角色统计数据 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        try {
            return Result.ok("获取成功", roleService.getRoleStats());
        } catch (Exception e) {
            log.error("获取角色统计数据失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取用户的角色列表 */
    @GetMapping("/user-roles/{userId}")
    public Result<List<Map<String, Object>>> getUserRoles(@PathVariable Long userId) {
        try {
            return Result.ok("获取成功", roleService.getUserRoles(userId));
        } catch (Exception e) {
            log.error("获取用户角色列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取所有可用角色（用于下拉选择） */
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> getAllAvailable() {
        try {
            return Result.ok("获取成功", roleService.getAllAvailableRoles());
        } catch (Exception e) {
            log.error("获取所有可用角色失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 新增角色 */
    @PostMapping("/create")
    public Result<String> create(@RequestBody Map<String, Object> params) {
        try {
            roleService.createRole(params);
            log.info("新增角色成功: {}", params.get("roleName"));
            return Result.ok("新增成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增角色失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    /** 编辑角色 */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> params) {
        try {
            roleService.updateRole(params);
            log.info("编辑角色成功: id={}", params.get("id"));
            return Result.ok("修改成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑角色失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    /** 删除角色 */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            log.info("删除角色成功: id={}", id);
            return Result.ok("删除成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除角色失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /** 获取角色已分配的权限ID列表 */
    @GetMapping("/permissions/{roleId}")
    public Result<List<Long>> getRolePermissions(@PathVariable Long roleId) {
        try {
            return Result.ok("获取成功", roleService.getRolePermissions(roleId));
        } catch (Exception e) {
            log.error("获取角色权限失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 保存角色权限分配 */
    @PostMapping("/assign-permissions")
    public Result<String> assignPermissions(@RequestBody Map<String, Object> params) {
        try {
            Long roleId = Long.valueOf(params.get("roleId").toString());
            @SuppressWarnings("unchecked")
            List<Object> rawIds = (List<Object>) params.get("permissionIds");
            List<Long> permissionIds = new ArrayList<>();
            if (rawIds != null) {
                for (Object id : rawIds) {
                    if (id != null) permissionIds.add(Long.valueOf(id.toString()));
                }
            }
            roleService.assignPermissions(roleId, permissionIds);
            log.info("保存角色权限成功: roleId={}, 权限数={}", roleId, permissionIds.size());
            return Result.ok("权限保存成功");
        } catch (Exception e) {
            log.error("保存角色权限失败", e);
            return Result.error("保存失败: " + e.getMessage());
        }
    }
}
