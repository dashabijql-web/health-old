package com.xzkj.health.service.watch;

import com.alibaba.fastjson2.JSON;
import com.xzkj.health.mapper.UserListMapper;
import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceDataBuffer;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.service.DeviceDataBufferService;
import com.xzkj.health.service.RedisHealthBufferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchDataPersistenceServiceTest {

    @Mock
    private DeviceDataBufferService deviceDataBufferService;

    @Mock
    private UserListMapper userListMapper;

    @Mock
    private RedisHealthBufferService redisHealthBufferService;

    @Mock
    private WatchHealthWarningService watchHealthWarningService;

    @InjectMocks
    private WatchDataPersistenceService service;

    @Test
    void persistWritesUnboundDataToDeviceBuffer() {
        Device device = new Device();
        device.setId(12L);
        WatchDeviceContext context = new WatchDeviceContext(device, null);
        Map<String, Object> data = Map.of("steps", 123, "calories", 45);

        service.persist(context, "123456789012345", "heartbeat", data);

        ArgumentCaptor<DeviceDataBuffer> bufferCaptor = ArgumentCaptor.forClass(DeviceDataBuffer.class);
        verify(deviceDataBufferService).save(bufferCaptor.capture());

        DeviceDataBuffer buffer = bufferCaptor.getValue();
        assertEquals(12L, buffer.getDeviceId());
        assertEquals("123456789012345", buffer.getImei());
        assertEquals("heartbeat", buffer.getDataType());
        assertFalse(buffer.getIsTransferred());
        assertNotNull(buffer.getRecordTime());
        assertNotNull(buffer.getReceiveTime());

        @SuppressWarnings("unchecked")
        Map<String, Object> json = JSON.parseObject(buffer.getDataJson(), Map.class);
        assertEquals(123, json.get("steps"));
        assertEquals(45, json.get("calories"));
    }

    @Test
    void persistWritesBoundDataToRedisAndEvaluatesWarnings() {
        Device device = new Device();
        device.setId(12L);
        DeviceUser binding = new DeviceUser();
        binding.setEmpId(7L);
        WatchDeviceContext context = new WatchDeviceContext(device, binding);
        Map<String, Object> empInfo = new HashMap<>();
        empInfo.put("userCode", "E001");
        empInfo.put("riskLevel", 2);
        when(userListMapper.getEmpDetail(7L)).thenReturn(empInfo);

        Map<String, Object> data = new HashMap<>();
        data.put("heart_rate", 82);
        data.put("pressure", 61);
        data.put("blood_oxygen", 96);

        service.persist(context, "123456789012345", "heart_rate", data);

        ArgumentCaptor<HealthRecord> recordCaptor = ArgumentCaptor.forClass(HealthRecord.class);
        verify(redisHealthBufferService).push(recordCaptor.capture());
        HealthRecord record = recordCaptor.getValue();
        assertEquals("E001", record.getUserCode());
        assertEquals(82, record.getHeartRate());
        assertEquals(61, record.getPressure());
        assertEquals(96, record.getBloodOxygen());
        assertNotNull(record.getTime());
        verify(watchHealthWarningService).evaluate(eq("E001"), same(record), eq(2));
    }
}
