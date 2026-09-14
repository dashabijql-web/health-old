package com.xzkj.health.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xzkj.health.mapper.DeviceUserMapper;
import com.xzkj.health.mapper.UserListMapper;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.service.DeviceUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备用户关联Service实现
 */
@Slf4j
@Service
public class DeviceUserServiceImpl extends ServiceImpl<DeviceUserMapper, DeviceUser> implements DeviceUserService {

    @Autowired
    private UserListMapper userListMapper;

    @Override
    public DeviceUser getCurrentBinding(Long deviceId) {
        return baseMapper.selectCurrentBinding(deviceId);
    }

    @Override
    public Map<Long, DeviceUser> getAllCurrentBindings() {
        List<DeviceUser> list = baseMapper.selectAllCurrentBindings();
        Map<Long, DeviceUser> map = new HashMap<>();
        if (list != null) {
            for (DeviceUser du : list) {
                map.put(du.getDeviceId(), du);
            }
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindDeviceToUser(Long deviceId, Long userId) {
        // 检查设备是否已绑定
        DeviceUser currentBinding = getCurrentBinding(deviceId);
        if (currentBinding != null) {
            throw new IllegalArgumentException("设备已绑定用户，请先解绑");
        }

        // 检查员工是否已绑定其他设备（一个员工只能绑定一个手表）
        DeviceUser empBinding = baseMapper.selectUserCurrentBinding(userId);
        if (empBinding != null) {
            throw new IllegalArgumentException("该员工已绑定其他设备，一个员工只能绑定一个手表");
        }

        // 查询员工信息
        Map<String, Object> empInfo = userListMapper.getEmpDetail(userId);
        if (empInfo == null) {
            throw new IllegalArgumentException("员工不存在");
        }

        // 创建绑定记录
        DeviceUser binding = new DeviceUser();
        binding.setDeviceId(deviceId);
        binding.setEmpId(userId);
        binding.setRealName((String) empInfo.get("realName"));
        binding.setDeptName((String) empInfo.get("deptName"));
        binding.setBindTime(LocalDateTime.now());
        binding.setUnbindTime(null);

        save(binding);
        log.info("设备绑定成功: deviceId={}, empId={}, empName={}",
                deviceId, userId, binding.getRealName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevice(Long deviceId) {
        // 查询当前绑定
        DeviceUser currentBinding = getCurrentBinding(deviceId);
        if (currentBinding == null) {
            throw new IllegalArgumentException("设备未绑定用户");
        }

        // 设置解绑时间
        currentBinding.setUnbindTime(LocalDateTime.now());
        updateById(currentBinding);

        log.info("设备解绑成功: deviceId={}, empId={}", deviceId, currentBinding.getEmpId());
    }
}
