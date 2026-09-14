package com.xzkj.health.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xzkj.health.model.DeviceUser;

import java.util.Map;

/**
 * 设备用户关联Service
 */
public interface DeviceUserService extends IService<DeviceUser> {

    /**
     * 获取设备当前绑定的用户
     */
    DeviceUser getCurrentBinding(Long deviceId);

    /**
     * 批量获取所有设备的当前绑定，返回 Map&lt;deviceId, DeviceUser&gt;
     */
    Map<Long, DeviceUser> getAllCurrentBindings();

    /**
     * 绑定设备到用户
     * @param deviceId 设备ID
     * @param userId 用户ID
     */
    void bindDeviceToUser(Long deviceId, Long userId);

    /**
     * 解绑设备
     * @param deviceId 设备ID
     */
    void unbindDevice(Long deviceId);
}
