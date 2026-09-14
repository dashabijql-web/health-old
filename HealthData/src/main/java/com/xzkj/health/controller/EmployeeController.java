package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.dto.employee.EmployeeCommandSearchView;
import com.xzkj.health.model.entity.Employee;
import com.xzkj.health.service.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping("/list")
    public Result<List<Employee>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long jobTypeId,
            @RequestParam(required = false) Integer status) {
        try {
            List<Employee> data = employeeService.list(keyword, deptId, jobTypeId, status);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取员工列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/list-detail")
    public Result<List<Map<String, Object>>> listWithDetails() {
        try {
            List<Map<String, Object>> data = employeeService.listWithDetails();
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取员工详情列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/command-search")
    public Result<List<EmployeeCommandSearchView>> searchForCommand(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "12") int limit) {
        return Result.ok("获取成功", employeeService.searchForCommand(query, limit));
    }

    @GetMapping("/detail/{id}")
    public Result<Employee> getById(@PathVariable Long id) {
        try {
            Employee data = employeeService.getById(id);
            if (data != null) {
                return Result.ok("获取成功", data);
            } else {
                return Result.error("员工不存在");
            }
        } catch (Exception e) {
            log.error("获取员工详情失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> raw = employeeService.getStats();
            long total     = raw.get("totalCount")  != null ? ((Number) raw.get("totalCount")).longValue()  : 0L;
            long active    = raw.get("activeCount")  != null ? ((Number) raw.get("activeCount")).longValue()  : 0L;
            long maleCount = raw.get("maleCount")    != null ? ((Number) raw.get("maleCount")).longValue()    : 0L;
            int  maleRatio = total > 0 ? (int) (maleCount * 100L / total) : 0;
            long online    = employeeService.getTodayOnlineCount();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total",     total);
            data.put("active",    active);
            data.put("online",    online);
            data.put("maleRatio", maleRatio);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取员工统计数据失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<?> create(@RequestBody Employee employee) {
        try {
            employeeService.create(employee);
            log.info("新增员工成功: {}", employee.getEmpName());
            return Result.ok("新增成功");
        } catch (Exception e) {
            log.error("新增员工失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody Employee employee) {
        try {
            employeeService.update(employee);
            log.info("修改员工成功: id={}", employee.getId());
            return Result.ok("修改成功");
        } catch (Exception e) {
            log.error("修改员工失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        try {
            employeeService.delete(id);
            log.info("删除员工成功: id={}", id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("删除员工失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
