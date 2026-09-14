package com.xzkj.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.mapper.DeviceMapper;
import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.service.DeviceService;
import com.xzkj.health.service.DeviceUserService;
import com.xzkj.health.config.datasource.WatchDataSourceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备Service实现
 */
@Slf4j
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceUserService deviceUserService;

    @Autowired
    private WatchDataSourceResolver watchDataSourceResolver;

    @Override
    public Device getOrCreateByImei(String imei) {
        // 先查询是否存在
        Device device = baseMapper.selectByImei(imei);
        if (device != null) {
            return device;
        }

        // 不存在则创建新设备
        device = new Device();
        device.setImei(imei);
        device.setStatus(1); // 在线
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());
        device.setLastOnlineTime(LocalDateTime.now());

        baseMapper.insert(device);
        log.info("自动注册新设备: IMEI={}, ID={}", imei, device.getId());

        return device;
    }

    @Override
    public void updateOnlineStatus(Long deviceId, String battery) {
        Device device = baseMapper.selectById(deviceId);
        if (device != null) {
            device.setStatus(1); // 在线
            device.setLastOnlineTime(LocalDateTime.now());
            device.setUpdateTime(LocalDateTime.now());
            if (battery != null && !battery.isBlank()) {
                Integer batteryLevel = normalizeBatteryLevel(battery);
                if (batteryLevel != null) {
                    device.setBatteryLevel(batteryLevel);
                }
            }
            baseMapper.updateById(device);
        }
    }

    static Integer normalizeBatteryLevel(String battery) {
        try {
            int parsed = Integer.parseInt(battery.trim());
            return parsed >= 1 && parsed <= 100 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    @Async
    public void updateOfflineStatus(String imei) {
        HealthDataSourceContext.runWith(watchDataSourceResolver.resolveWatchSource(imei), () -> {
            Device device = baseMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getImei, imei)
            );
            if (device != null) {
                device.setStatus(0);
                device.setUpdateTime(LocalDateTime.now());
                baseMapper.updateById(device);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferBufferDataToUser(Long deviceId, Long userId) {
        try {
            // 1. 检查设备绑定状态
            DeviceUser currentBinding = deviceUserService.getCurrentBinding(deviceId);

            // 2. 如果设备已绑定，只能转移到绑定的用户
            if (currentBinding != null && !currentBinding.getEmpId().equals(userId)) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("result", 0);
                errorResult.put("message", "设备已绑定到员工「" + currentBinding.getRealName() + "」，不能转移数据到其他员工");
                errorResult.put("transferred_count", 0);
                log.warn("拒绝转移数据: deviceId={} 已绑定到 empId={}, 尝试转移到 empId={}",
                        deviceId, currentBinding.getEmpId(), userId);
                return errorResult;
            }

            // 3. 未绑定或绑定给目标用户，允许转移
            String sql = "EXEC sp_transfer_buffer_to_health @device_id = ?, @user_id = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, deviceId, userId);

            Integer resultCode = (Integer) result.get("result");
            String message = (String) result.get("message");
            Integer transferredCount = (Integer) result.get("transferred_count");

            if (resultCode == 1) {
                log.info("设备缓冲数据转移成功: deviceId={}, userId={}, count={}",
                        deviceId, userId, transferredCount);
            } else {
                log.error("设备缓冲数据转移失败: deviceId={}, userId={}, message={}",
                        deviceId, userId, message);
            }

            return result;

        } catch (Exception e) {
            log.error("转移设备缓冲数据异常: deviceId={}, userId={}", deviceId, userId, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("result", 0);
            errorResult.put("message", "转移数据失败: " + e.getMessage());
            errorResult.put("transferred_count", 0);
            return errorResult;
        }
    }
}
