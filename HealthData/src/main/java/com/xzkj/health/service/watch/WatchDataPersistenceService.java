package com.xzkj.health.service.watch;

import com.alibaba.fastjson2.JSON;
import com.xzkj.health.mapper.UserListMapper;
import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceDataBuffer;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.service.DeviceDataBufferService;
import com.xzkj.health.service.RedisHealthBufferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class WatchDataPersistenceService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceDataBufferService deviceDataBufferService;
    private final UserListMapper userListMapper;
    private final RedisHealthBufferService redisHealthBufferService;
    private final WatchHealthWarningService watchHealthWarningService;

    public WatchDataPersistenceService(DeviceDataBufferService deviceDataBufferService,
                                       UserListMapper userListMapper,
                                       RedisHealthBufferService redisHealthBufferService,
                                       WatchHealthWarningService watchHealthWarningService) {
        this.deviceDataBufferService = deviceDataBufferService;
        this.userListMapper = userListMapper;
        this.redisHealthBufferService = redisHealthBufferService;
        this.watchHealthWarningService = watchHealthWarningService;
    }

    public void persist(WatchDeviceContext context, String imei, String dataType, Map<String, Object> dataMap) {
        if (context.currentBinding() == null) {
            saveToBuffer(context.device(), imei, dataType, dataMap);
        } else {
            saveToHealthRecord(imei, context.currentBinding(), dataMap);
        }
    }

    private void saveToBuffer(Device device, String imei, String dataType, Map<String, Object> dataMap) {
        try {
            DeviceDataBuffer buffer = new DeviceDataBuffer();
            buffer.setDeviceId(device.getId());
            buffer.setImei(imei);
            buffer.setDataType(dataType);
            buffer.setDataJson(JSON.toJSONString(dataMap));
            buffer.setRecordTime(LocalDateTime.now());
            buffer.setReceiveTime(LocalDateTime.now());
            buffer.setIsTransferred(false);

            deviceDataBufferService.save(buffer);
            log.debug("数据已暂存到缓冲表: IMEI={}, 类型={}, 数据={}", imei, dataType, dataMap);
        } catch (Exception e) {
            log.error("保存到缓冲表失败: IMEI={}, 类型={}", imei, dataType, e);
        }
    }

    private void saveToHealthRecord(String imei, DeviceUser currentBind, Map<String, Object> dataMap) {
        try {
            Map<String, Object> empInfo = userListMapper.getEmpDetail(currentBind.getEmpId());
            if (empInfo == null) {
                log.error("无法获取员工信息: empId={}", currentBind.getEmpId());
                return;
            }

            HealthRecord record = toHealthRecord(empInfo, dataMap);
            redisHealthBufferService.push(record);

            log.debug("数据已推入Redis缓冲: IMEI={}, userCode={}, 数据={}",
                    imei, record.getUserCode(), dataMap);

            Object rlObj = empInfo.get("riskLevel");
            Integer riskLevel = rlObj instanceof Number ? ((Number) rlObj).intValue() : null;
            watchHealthWarningService.evaluate(record.getUserCode(), record, riskLevel);
        } catch (Exception e) {
            log.error("保存到健康记录失败: IMEI={}", imei, e);
        }
    }

    private HealthRecord toHealthRecord(Map<String, Object> empInfo, Map<String, Object> dataMap) {
        HealthRecord record = new HealthRecord();
        String userCode = (String) empInfo.get("userCode");
        record.setUserCode(userCode != null ? userCode : "");

        Object value;
        if ((value = dataMap.get("heart_rate")) instanceof Number)
            record.setHeartRate(((Number) value).intValue());
        if ((value = dataMap.get("blood_oxygen")) instanceof Number)
            record.setBloodOxygen(((Number) value).intValue());
        if ((value = dataMap.get("temperature")) instanceof Number)
            record.setTemperature(((Number) value).intValue());
        if ((value = dataMap.get("blood_pressure_high")) instanceof Number)
            record.setBloodPressureHigh(((Number) value).intValue());
        if ((value = dataMap.get("blood_pressure_low")) instanceof Number)
            record.setBloodPressureLow(((Number) value).intValue());
        if ((value = dataMap.get("pressure")) instanceof Number)
            record.setPressure(((Number) value).intValue());
        if ((value = dataMap.get("steps")) instanceof Number)
            record.setSteps(((Number) value).intValue());
        if ((value = dataMap.get("calories")) instanceof Number)
            record.setCalories(((Number) value).intValue());
        if ((value = dataMap.get("sleep_minutes")) instanceof Number)
            record.setSleepMinutes(((Number) value).intValue());

        record.setTime(LocalDateTime.now().format(FORMATTER));
        return record;
    }
}
