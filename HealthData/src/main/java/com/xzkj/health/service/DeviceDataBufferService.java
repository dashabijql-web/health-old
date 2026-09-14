package com.xzkj.health.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xzkj.health.model.DeviceDataBuffer;
import java.util.Map;

/**
 * 设备数据缓冲Service
 */
public interface DeviceDataBufferService extends IService<DeviceDataBuffer> {

    /**
     * 获取设备缓冲数据统计
     */
    Map<String, Object> getBufferStats(Long deviceId);

    /**
     * 批量获取所有设备的待处理缓冲数量，返回 Map&lt;deviceId, pendingCount&gt;
     */
    Map<Long, Integer> getAllPendingCounts();

    /**
     * 删除设备的所有缓冲数据
     */
    int deleteByDeviceId(Long deviceId);
}
