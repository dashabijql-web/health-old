package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.model.entity.AlertConfig;
import com.xzkj.health.service.AlertConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/alert-config")
public class AlertConfigController {

    @Autowired
    private AlertConfigService alertConfigService;

    @GetMapping("/list")
    public Result<List<AlertConfig>> listAll() {
        try {
            List<AlertConfig> data = alertConfigService.listAll();
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取告警配置列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody AlertConfig alertConfig) {
        try {
            alertConfigService.update(alertConfig);
            log.info("修改告警配置成功: id={}", alertConfig.getId());
            return Result.ok("修改成功");
        } catch (Exception e) {
            log.error("修改告警配置失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    @PutMapping("/toggle/{id}")
    public Result<?> toggle(@PathVariable Long id) {
        try {
            boolean ok = alertConfigService.toggle(id);
            if (ok) {
                log.info("切换告警配置状态成功: id={}", id);
                return Result.ok("操作成功");
            } else {
                return Result.error("配置不存在");
            }
        } catch (Exception e) {
            log.error("切换告警配置状态失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }
}
