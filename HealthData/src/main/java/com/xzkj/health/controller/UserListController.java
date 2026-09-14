package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.service.UserListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 用户列表控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserListController {

    @Autowired
    private UserListService userListService;

    /** 获取用户列表 */
    @GetMapping("/list")
    public Result<Map<String, Object>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            size = DateParamUtil.clampSize(size);
            Map<String, Object> data = userListService.getUserList(keyword, status, deptId, page, size);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 搜索用户（用于自动补全） */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchUsers(@RequestParam String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return Result.ok("查询成功", new ArrayList<>());
            }
            List<Map<String, Object>> users = userListService.searchUsers(query.trim());
            return Result.ok("查询成功", users);
        } catch (Exception e) {
            log.error("搜索用户失败: query={}", query, e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /** 获取用户详细信息 */
    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        try {
            Map<String, Object> data = userListService.getUserDetail(id);
            if (data != null) {
                return Result.ok("获取成功", data);
            } else {
                return Result.error("用户不存在");
            }
        } catch (Exception e) {
            log.error("获取用户详细信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取用户统计数据 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> data = userListService.getUserStats();
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取用户统计数据失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 获取用户健康统计 */
    @GetMapping("/health-stats/{userCode}")
    public Result<Map<String, Object>> getHealthStats(@PathVariable String userCode) {
        try {
            Map<String, Object> data = userListService.getUserHealthStats(userCode);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取用户健康统计失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 新增用户 */
    @PostMapping("/create")
    public Result<String> create(@RequestBody Map<String, Object> params) {
        try {
            userListService.createUser(params);
            log.info("新增用户成功: {}", params.get("realName"));
            return Result.ok("新增成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增用户失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    /** 编辑用户 */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> params) {
        try {
            if (params.get("id") == null) {
                return Result.error("用户ID不能为空");
            }
            userListService.updateUser(params);
            log.info("编辑用户成功: id={}", params.get("id"));
            return Result.ok("修改成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑用户失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    /** 删除用户 */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            userListService.deleteUser(id);
            log.info("删除用户成功: id={}", id);
            return Result.ok("删除成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /** 修改用户状态 */
    @PutMapping("/status")
    public Result<String> updateStatus(@RequestBody Map<String, Object> params) {
        try {
            if (params.get("id") == null || params.get("status") == null) {
                return Result.error("id 和 status 不能为空");
            }
            Long id = Long.valueOf(params.get("id").toString());
            Integer status = Integer.valueOf(params.get("status").toString());
            userListService.updateUserStatus(id, status);
            log.info("修改用户状态成功: id={}, status={}", id, status);
            return Result.ok("状态修改成功");
        } catch (Exception e) {
            log.error("修改用户状态失败", e);
            return Result.error("状态修改失败: " + e.getMessage());
        }
    }
}
