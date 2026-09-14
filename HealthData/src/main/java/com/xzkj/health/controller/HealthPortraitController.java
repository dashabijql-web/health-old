package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.dto.portrait.HealthPortraitView;
import com.xzkj.health.service.HealthPortraitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/health-portrait")
public class HealthPortraitController {

    @Autowired
    private HealthPortraitService healthPortraitService;

    @GetMapping("/{empCode}")
    public Result<HealthPortraitView> getPortrait(@PathVariable String empCode) {
        return Result.ok("获取成功", healthPortraitService.getPortrait(empCode));
    }
}
