package com.xzkj.health.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzkj.health.common.Result;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.service.HealthRecordService;
import com.xzkj.health.dto.healthrecord.EmployeeHealthHistoryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health/record")
public class HealthRecordController {

    @Autowired
    private HealthRecordService healthRecordService;

    /**
     * 分页查询（支持 userCode / startTime / endTime / pageSize 过滤）
     * pageSize 是 size 的别名，兼容前端旧参数名
     */
    @GetMapping("/page")
    public Result<Map<String, Object>> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String userCode,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        int effectiveSize = Math.min((pageSize != null) ? pageSize : size, 200);
        Page<HealthRecord> page = new Page<>(current, effectiveSize);
        IPage<HealthRecord> pageResult = healthRecordService.getPageFiltered(page, userCode, startTime, endTime);

        Map<String, Object> data = new HashMap<>();
        data.put("records", pageResult.getRecords());
        data.put("total",   pageResult.getTotal());
        data.put("current", pageResult.getCurrent());
        data.put("size",    pageResult.getSize());
        return Result.ok("查询成功", data);
    }

    @GetMapping("/history/trend")
    public Result<EmployeeHealthHistoryView> getEmployeeHistory(
            @RequestParam String userCode,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return Result.ok("查询成功", healthRecordService.getEmployeeHistory(userCode, startDate, endDate));
    }
}
