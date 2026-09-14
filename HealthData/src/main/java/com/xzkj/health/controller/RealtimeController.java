package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.dto.realtime.RealtimeAlertView;
import com.xzkj.health.dto.realtime.RealtimeHealthSnapshotView;
import com.xzkj.health.dto.realtime.RealtimeOverviewView;
import com.xzkj.health.dto.realtime.RealtimeStatisticsView;
import com.xzkj.health.dto.realtime.RealtimeUserDetailView;
import com.xzkj.health.dto.realtime.RealtimeUserPageView;
import com.xzkj.health.service.RealtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实时监控控制器
 */
@RestController
@RequestMapping("/realtime")
public class RealtimeController {

    @Autowired
    private RealtimeService realtimeService;

    /** 获取当日平均数据概览 */
    @GetMapping("/overview")
    public Result<RealtimeOverviewView> getOverview() {
        return Result.ok("获取成功", realtimeService.getTodayAvgOverview());
    }

    /** 获取在线用户列表（分页） */
    @GetMapping("/online-users")
    public Result<RealtimeUserPageView> getOnlineUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String indicator) {
        return Result.ok("获取成功", realtimeService.getOnlineUsers(page, size, name, dept, status, indicator));
    }

    /** 获取统一管控使用的实时健康快照。 */
    @GetMapping("/health-snapshot")
    public Result<RealtimeHealthSnapshotView> getHealthSnapshot() {
        return Result.ok("获取成功", realtimeService.getHealthSnapshot());
    }

    /** 获取单个用户实时数据 */
    @GetMapping("/user/{userCode}")
    public Result<RealtimeUserDetailView> getUserData(@PathVariable String userCode) {
        return Result.ok("获取成功", realtimeService.getUserRealtimeData(userCode));
    }

    /** 获取实时统计汇总 */
    @GetMapping("/statistics")
    public Result<RealtimeStatisticsView> getStatistics() {
        return Result.ok("获取成功", realtimeService.getStatistics());
    }

    /** 获取近期未处理告警列表（安全指挥中心实时告警栏） */
    @GetMapping("/alerts")
    public Result<List<RealtimeAlertView>> getAlerts(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok("获取成功", realtimeService.getRealtimeAlerts(limit));
    }
}
