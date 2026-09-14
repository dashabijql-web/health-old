package com.xzkj.health.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xzkj.health.mapper.DeviceDataBufferMapper;
import com.xzkj.health.model.DeviceDataBuffer;
import com.xzkj.health.service.DeviceDataBufferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备数据缓冲Service实现
 */
@Slf4j
@Service
public class DeviceDataBufferServiceImpl
        extends ServiceImpl<DeviceDataBufferMapper, DeviceDataBuffer>
        implements DeviceDataBufferService {

    @Override
    public Map<String, Object> getBufferStats(Long deviceId) {
        return baseMapper.getBufferStatsByDevice(deviceId);
    }

    @Override
    public Map<Long, Integer> getAllPendingCounts() {
        List<Map<String, Object>> rows = baseMapper.selectAllPendingCounts();
        Map<Long, Integer> result = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object devId = row.get("device_id");
                Object cnt   = row.get("pending_count");
                if (devId instanceof Number) {
                    int pending = (cnt instanceof Number) ? ((Number) cnt).intValue() : 0;
                    result.put(((Number) devId).longValue(), pending);
                }
            }
        }
        return result;
    }

    @Override
    public int deleteByDeviceId(Long deviceId) {
        return baseMapper.deleteByDeviceId(deviceId);
    }
}
