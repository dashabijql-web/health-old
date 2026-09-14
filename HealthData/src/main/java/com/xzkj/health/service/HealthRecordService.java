package com.xzkj.health.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.dto.healthrecord.EmployeeHealthHistoryView;

import java.util.List;
import java.util.Map;

public interface HealthRecordService extends IService<HealthRecord> {

    /**
     * 根据用户代码查询记录
     */
    List<HealthRecord> getByUserCode(String userCode);

    /**
     * 获取用户最新的健康记录
     */
    HealthRecord getLatestByUserCode(String userCode);

    /**
     * 查询异常心率记录
     */
    List<HealthRecord> getAbnormalHeartRate();

    /**
     * 按时间范围查询
     */
    List<HealthRecord> getByTimeRange(String startTime, String endTime);

    /**
     * 分页查询（无过滤）
     */
    Page<HealthRecord> getPage(Page<HealthRecord> page);

    /**
     * 分页查询（支持按 userCode / startTime / endTime 过滤）
     */
    Page<HealthRecord> getPageFiltered(Page<HealthRecord> page,
                                       String userCode,
                                       String startTime,
                                       String endTime);

    EmployeeHealthHistoryView getEmployeeHistory(String userCode, String startDate, String endDate);

    /**
     * 统计每个用户的记录数量
     */
    List<Map<String, Object>> countByUser();

    /**
     * 获取健康数据统计
     */
    Map<String, Object> getHealthStatistics(String userCode);

    /**
     * 批量插入数据
     */
    boolean batchInsert(List<HealthRecord> records);
}
