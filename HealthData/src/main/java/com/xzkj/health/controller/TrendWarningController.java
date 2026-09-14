package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.dto.trendwarning.TrendWarningPredictionView;
import com.xzkj.health.service.TrendWarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trend-warning")
public class TrendWarningController {

    @Autowired
    private TrendWarningService trendWarningService;

    @GetMapping("/predict")
    public Result<TrendWarningPredictionView> predict() {
        return Result.ok("获取成功", trendWarningService.predict());
    }
}
