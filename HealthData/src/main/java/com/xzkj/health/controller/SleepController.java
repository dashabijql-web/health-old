package com.xzkj.health.controller;

import com.xzkj.health.common.DateParamUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.dto.sleep.SleepPageDataView;
import com.xzkj.health.dto.sleep.SleepQualityDistributionView;
import com.xzkj.health.dto.sleep.SleepTrendView;
import com.xzkj.health.service.SleepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 睡眠监测控制器
 */
@RestController
@RequestMapping("/sleep")
public class SleepController {

    @Autowired
    private SleepService sleepService;

    /** 获取睡眠趋势数据 */
    @GetMapping("/trend")
    public Result<SleepTrendView> getTrend(
            @RequestParam(defaultValue = "7") Integer days) {
        days = DateParamUtil.clampDays(days);
        return Result.ok("获取成功", sleepService.getSleepTrend(days));
    }

    /** 获取睡眠质量分布 */
    @GetMapping("/quality-distribution")
    public Result<SleepQualityDistributionView> getQualityDistribution() {
        return Result.ok("获取成功", sleepService.getQualityDistribution());
    }

    /** 获取睡眠页面完整数据 */
    @GetMapping("/page-data")
    public Result<SleepPageDataView> getPageData() {
        return Result.ok("获取成功", sleepService.getSleepPageData());
    }
}
