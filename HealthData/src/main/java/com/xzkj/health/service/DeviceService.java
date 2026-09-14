package com.xzkj.health.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xzkj.health.model.Device;
import java.util.Map;

/**
 * 设备Service
 */
public interface DeviceService extends IService<Device> {

    /**
     * 根据IMEI获取或创建设备
     */
    Device getOrCreateByImei(String imei);

    /**
     * 更新设备在线状态
     */
    void updateOnlineStatus(Long deviceId, String battery);

    /**
     * 更新设备离线状态（设备断连时调用）
     */
    void updateOfflineStatus(String imei);

    /**
     * 转移设备缓冲数据到用户
     * @param deviceId 设备ID
     * @param userId 用户ID
     * @return 转移结果
     */
    Map<String, Object> transferBufferDataToUser(Long deviceId, Long userId);
}
