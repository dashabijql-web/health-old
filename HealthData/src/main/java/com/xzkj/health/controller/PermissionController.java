package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限管理控制器
 * GET /permission/tree  →  返回权限树（供前端分配权限使用）
 */
@Slf4j
@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Autowired
    private RoleService roleService;

    /**
     * 获取完整权限树
     * 前端分配权限弹窗调用此接口加载 el-tree 数据
     */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> getPermissionTree() {
        try {
            List<Map<String, Object>> tree = roleService.getPermissionTree();
            return Result.ok("获取成功", tree);
        } catch (Exception e) {
            log.error("获取权限树失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}
