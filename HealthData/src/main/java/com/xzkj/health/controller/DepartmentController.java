package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.model.entity.Department;
import com.xzkj.health.service.DepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @GetMapping("/list")
    public Result<List<Department>> list(@RequestParam(required = false) String keyword) {
        try {
            List<Department> data = departmentService.list(keyword);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取部门列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        try {
            List<Map<String, Object>> data = departmentService.tree();
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取部门树失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<?> create(@RequestBody Department department) {
        try {
            departmentService.create(department);
            log.info("新增部门成功: {}", department.getDeptName());
            return Result.ok("新增成功");
        } catch (Exception e) {
            log.error("新增部门失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody Department department) {
        try {
            departmentService.update(department);
            log.info("修改部门成功: id={}", department.getId());
            return Result.ok("修改成功");
        } catch (Exception e) {
            log.error("修改部门失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        try {
            departmentService.delete(id);
            log.info("删除部门成功: id={}", id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("删除部门失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
