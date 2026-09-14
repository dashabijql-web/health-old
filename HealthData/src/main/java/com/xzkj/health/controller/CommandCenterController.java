package com.xzkj.health.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.dto.commandcenter.AssignCommandCenterIncidentRequest;
import com.xzkj.health.dto.commandcenter.CommandCenterActionResultView;
import com.xzkj.health.dto.commandcenter.CommandCenterDashboardSummaryView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentPageView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentActionRequest;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentTimelineItemView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentView;
import com.xzkj.health.dto.commandcenter.PreShiftReviewActionRequest;
import com.xzkj.health.dto.commandcenter.PreShiftReviewView;
import com.xzkj.health.dto.commandcenter.ResolveCommandCenterIncidentRequest;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.CommandCenterIncidentService;
import com.xzkj.health.service.CommandCenterDashboardSummaryService;
import com.xzkj.health.service.PreShiftReviewService;
import com.xzkj.health.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Unified incident surface shared by the safety command and dashboard pages. */
@RestController
@RequestMapping("/command-center")
@RequiredArgsConstructor
public class CommandCenterController {

    private final CommandCenterIncidentService incidentService;
    private final CommandCenterDashboardSummaryService dashboardSummaryService;
    private final PreShiftReviewService preShiftReviewService;
    private final SysUserService sysUserService;

    @GetMapping("/dashboard-summary")
    public Result<CommandCenterDashboardSummaryView> getDashboardSummary(
            @RequestParam(defaultValue = "day") String period) {
        return Result.ok("获取成功", dashboardSummaryService.getSummary(period));
    }

    @GetMapping("/pre-shift-reviews")
    public Result<List<PreShiftReviewView>> getPreShiftReviews(
            @RequestParam(required = false) String status) {
        return Result.ok("获取成功", preShiftReviewService.getTodayReviews(status));
    }

    @PostMapping("/pre-shift-reviews/{empCode}/action")
    public Result<PreShiftReviewView> applyPreShiftReviewAction(
            @PathVariable String empCode,
            @RequestBody PreShiftReviewActionRequest request) {
        return Result.ok("复检状态已更新",
                preShiftReviewService.applyAction(empCode, request, currentOperator()));
    }

    @GetMapping("/incidents")
    public Result<CommandCenterIncidentPageView> getIncidents(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok("获取成功", incidentService.getIncidents(
                scope, status, startAt, endAt, Math.max(1, page), DateParamUtil.clampSize(size)));
    }

    @GetMapping("/incidents/{warningId}")
    public Result<CommandCenterIncidentView> getIncident(
            @PathVariable Long warningId,
            @RequestParam(required = false) String occurredAt) {
        return Result.ok("获取成功", incidentService.getIncident(warningId, occurredAt));
    }

    @PostMapping("/incidents/{warningId}/resolve")
    public Result<CommandCenterActionResultView> resolveIncident(
            @PathVariable Long warningId,
            @RequestBody(required = false) ResolveCommandCenterIncidentRequest request) {
        String operator = currentOperator();
        String occurredAt = request == null ? null : request.occurredAt();
        String remark = request == null ? null : request.remark();
        return Result.ok("事件已处理", incidentService.resolveIncident(warningId, occurredAt, operator, remark));
    }

    @PostMapping("/incidents/{warningId}/ack")
    public Result<CommandCenterActionResultView> acknowledgeIncident(
            @PathVariable Long warningId,
            @RequestBody(required = false) CommandCenterIncidentActionRequest request) {
        return Result.ok("事件已确认", incidentService.acknowledgeIncident(
                warningId,
                request == null ? null : request.occurredAt(),
                currentOperator(),
                request == null ? null : request.remark()));
    }

    @PostMapping("/incidents/{warningId}/assign")
    public Result<CommandCenterActionResultView> assignIncident(
            @PathVariable Long warningId,
            @RequestBody AssignCommandCenterIncidentRequest request) {
        if (request == null || request.ownerUserId() == null) {
            throw new BusinessException(400, "分派必须指定责任人");
        }
        SysUser owner = sysUserService.getById(request.ownerUserId());
        if (owner == null || Integer.valueOf(1).equals(owner.getStatus())) {
            throw new BusinessException(400, "责任人不存在或已禁用");
        }
        String ownerName = displayName(owner);
        return Result.ok("事件已分派", incidentService.assignIncident(
                warningId,
                request.occurredAt(),
                owner.getId(),
                ownerName,
                null,
                request.slaMinutes(),
                currentOperator(),
                request.remark()));
    }

    @PostMapping("/incidents/{warningId}/false-alarm")
    public Result<CommandCenterActionResultView> falseAlarmIncident(
            @PathVariable Long warningId,
            @RequestBody(required = false) CommandCenterIncidentActionRequest request) {
        return Result.ok("误报已关闭", incidentService.falseAlarmIncident(
                warningId,
                request == null ? null : request.occurredAt(),
                currentOperator(),
                request == null ? null : request.remark()));
    }

    @PostMapping("/incidents/{warningId}/call")
    public Result<CommandCenterActionResultView> callIncidentContact(
            @PathVariable Long warningId,
            @RequestBody(required = false) CommandCenterIncidentActionRequest request) {
        return externalAction(warningId, request, "CALL");
    }

    @PostMapping("/incidents/{warningId}/broadcast")
    public Result<CommandCenterActionResultView> broadcastIncidentArea(
            @PathVariable Long warningId,
            @RequestBody(required = false) CommandCenterIncidentActionRequest request) {
        return externalAction(warningId, request, "BROADCAST");
    }

    @PostMapping("/incidents/{warningId}/evacuate")
    public Result<CommandCenterActionResultView> evacuateIncidentArea(
            @PathVariable Long warningId,
            @RequestBody(required = false) CommandCenterIncidentActionRequest request) {
        return externalAction(warningId, request, "EVACUATE");
    }

    @GetMapping("/incidents/{warningId}/timeline")
    public Result<List<CommandCenterIncidentTimelineItemView>> getTimeline(
            @PathVariable Long warningId,
            @RequestParam(required = false) String occurredAt) {
        return Result.ok("获取成功", incidentService.getIncidentTimeline(warningId, occurredAt));
    }

    private Result<CommandCenterActionResultView> externalAction(
            Long warningId, CommandCenterIncidentActionRequest request, String action) {
        CommandCenterActionResultView result = incidentService.recordExternalAction(
                warningId,
                request == null ? null : request.occurredAt(),
                action,
                currentOperator(),
                request == null ? null : request.target(),
                request == null ? null : request.remark());
        return Result.ok("动作已记录", result);
    }

    private String currentOperator() {
        StpUtil.checkLogin();
        SysUser user = sysUserService.getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在");
        }
        return displayName(user);
    }

    private String displayName(SysUser user) {
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername() : user.getRealName();
    }
}
