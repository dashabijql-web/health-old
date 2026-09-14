package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.dto.statistics.DailyRecordCountView;
import com.xzkj.health.dto.statistics.DeptHealthSummaryView;
import com.xzkj.health.dto.statistics.MonthlySummaryView;
import com.xzkj.health.dto.statistics.WarningTypeCountView;
import com.xzkj.health.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/dept-summary")
    public Result<List<DeptHealthSummaryView>> getDeptHealthSummary() {
        return Result.ok("获取成功", statisticsService.getDeptHealthSummary());
    }

    @GetMapping("/monthly-summary")
    public Result<List<MonthlySummaryView>> getMonthlySummary(@RequestParam String month) {
        return Result.ok("获取成功", statisticsService.getMonthlySummary(month));
    }

    @GetMapping("/daily-counts")
    public Result<List<DailyRecordCountView>> getDailyCounts(@RequestParam String month) {
        return Result.ok("获取成功", statisticsService.getDailyRecordCounts(month));
    }

    @GetMapping("/warning-types")
    public Result<List<WarningTypeCountView>> getWarningTypes(@RequestParam String month) {
        return Result.ok("获取成功", statisticsService.getWarningTypeCounts(month));
    }
}
