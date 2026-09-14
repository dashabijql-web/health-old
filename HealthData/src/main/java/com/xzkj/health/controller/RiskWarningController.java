package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.dto.riskwarning.RiskWarningDeptStatView;
import com.xzkj.health.dto.riskwarning.RiskWarningOverviewView;
import com.xzkj.health.dto.riskwarning.RiskWarningPageView;
import com.xzkj.health.dto.riskwarning.RiskWarningLocatorRequest;
import com.xzkj.health.dto.riskwarning.RiskWarningTrendView;
import com.xzkj.health.dto.riskwarning.RiskWarningTypeCountView;
import com.xzkj.health.service.RiskWarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 风险预警控制器
 */
@RestController
@RequestMapping("/risk-warning")
public class RiskWarningController {

    @Autowired
    private RiskWarningService riskWarningService;

    /** 获取风险预警统计概览 */
    @GetMapping("/overview")
    public Result<RiskWarningOverviewView> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", riskWarningService.getWarningStats(d[0], d[1]));
    }

    /** 获取预警列表（分页+过滤） */
    @GetMapping("/list")
    public Result<RiskWarningPageView> getList(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean handled,
            @RequestParam(required = false) String userCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warningType,
            @RequestParam(required = false) String eventSource,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        size = DateParamUtil.clampSize(size);
        return Result.ok("获取成功", riskWarningService.getWarningList(
                level, handled, userCode, keyword, warningType, eventSource, eventCode,
                startDate, endDate, Math.max(1, page), size));
    }

    /** 获取预警趋势（按类型分组） */
    @GetMapping("/trend")
    public Result<RiskWarningTrendView> getTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        days = DateParamUtil.clampDays(days);
        return Result.ok("获取成功", riskWarningService.getWarningTrend(days));
    }

    /** 获取各部门预警统计 */
    @GetMapping("/dept-stats")
    public Result<List<RiskWarningDeptStatView>> getDeptStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String[] d = DateParamUtil.range30(startDate, endDate);
        return Result.ok("获取成功", riskWarningService.getDeptWarningStats(d[0], d[1]));
    }

    /** 获取预警类型分布 */
    @GetMapping("/type-distribution")
    public Result<List<RiskWarningTypeCountView>> getTypeDistribution() {
        return Result.ok("获取成功", riskWarningService.getTypeDistribution());
    }

    /** 处理单条预警 */
    @PostMapping("/handle/{id}")
    public Result<String> handleWarning(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        String handleBy = params != null && params.get("handleBy") instanceof String
                ? (String) params.get("handleBy") : "system";
        String handleRemark = params != null && params.get("handleRemark") instanceof String
                ? (String) params.get("handleRemark") : "";
        String createTime = params != null && params.get("createTime") instanceof String
                ? (String) params.get("createTime") : null;
        boolean success = riskWarningService.handleWarning(id, handleBy, handleRemark, createTime);
        if (!success) {
            throw new BusinessException("处理失败，请确认预警ID是否存在");
        }
        return Result.ok("处理成功");
    }

    /** 批量处理预警 */
    @PostMapping("/handle-batch")
    public Result<String> handleBatch(@RequestBody List<RiskWarningLocatorRequest> locators) {
        boolean success = riskWarningService.handleBatch(locators, "system");
        if (!success) {
            throw new BusinessException("批量处理失败");
        }
        return Result.ok("处理成功");
    }
}
