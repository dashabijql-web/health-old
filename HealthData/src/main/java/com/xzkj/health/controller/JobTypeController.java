package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.model.entity.JobType;
import com.xzkj.health.service.JobTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/job-type")
public class JobTypeController {

    @Autowired
    private JobTypeService jobTypeService;

    @GetMapping("/list")
    public Result<List<JobType>> list(@RequestParam(required = false) String keyword) {
        try {
            List<JobType> data = jobTypeService.list(keyword);
            return Result.ok("获取成功", data);
        } catch (Exception e) {
            log.error("获取工种列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<?> create(@RequestBody JobType jobType) {
        try {
            jobTypeService.create(jobType);
            log.info("新增工种成功: {}", jobType.getTypeName());
            return Result.ok("新增成功");
        } catch (Exception e) {
            log.error("新增工种失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody JobType jobType) {
        try {
            jobTypeService.update(jobType);
            log.info("修改工种成功: id={}", jobType.getId());
            return Result.ok("修改成功");
        } catch (Exception e) {
            log.error("修改工种失败", e);
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        try {
            jobTypeService.delete(id);
            log.info("删除工种成功: id={}", id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("删除工种失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
