package com.xzkj.health.ai;

import com.xzkj.health.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 健康诊断报告 Controller
 *
 * POST /ai/report/employee  { "empCode": "EMP001" }
 * → 返回 Markdown 格式的健康诊断报告
 */
@Slf4j
@RestController
@RequestMapping("/ai/report")
public class AiReportController {

    @Autowired
    private AiReportService aiReportService;

    @PostMapping("/department")
    public Result<Map<String, String>> departmentReport(@RequestBody Map<String, String> body) {
        String deptName = body.get("deptName");
        if (deptName == null || deptName.isBlank()) {
            return Result.error(400, "deptName 不能为空");
        }
        try {
            String report = aiReportService.generateDepartmentReport(deptName.trim());
            return Result.ok(Map.of("report", report, "deptName", deptName));
        } catch (Exception e) {
            log.error("生成部门报告失败: {}", deptName, e);
            return Result.error("报告生成失败，请稍后重试");
        }
    }

    @PostMapping("/employee")
    public Result<Map<String, String>> employeeReport(@RequestBody Map<String, String> body) {
        String empCode = body.get("empCode");
        if (empCode == null || empCode.isBlank()) {
            return Result.error(400, "empCode 不能为空");
        }
        // 简单防注入：只允许字母数字
        if (!empCode.matches("[A-Za-z0-9_-]+")) {
            return Result.error(400, "empCode 格式不合法");
        }
        try {
            String report = aiReportService.generateEmployeeReport(empCode.trim().toUpperCase());
            return Result.ok(Map.of("report", report, "empCode", empCode));
        } catch (Exception e) {
            log.error("生成员工报告失败: {}", empCode, e);
            return Result.error("报告生成失败，请稍后重试");
        }
    }
}
