package com.xzkj.health.service;

import com.xzkj.health.config.CommandCenterOperationalProperties;
import com.xzkj.health.dto.commandcenter.DeviceOperationalStateView;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryRow;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryView;
import com.xzkj.health.mapper.DeviceOperationalMapper;
import com.xzkj.health.model.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceOperationalServiceTest {

    private DeviceOperationalMapper mapper;
    private DeviceOperationalService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DeviceOperationalMapper.class);
        CommandCenterOperationalProperties properties = new CommandCenterOperationalProperties();
        properties.setDeviceLowBatteryThreshold(20);
        properties.setDeviceDataInterruptedMinutes(15);
        service = new DeviceOperationalService(mapper, mock(DeviceService.class), properties);
        when(mapper.countSchemaTables()).thenReturn(1);
    }

    @Test
    void summaryUsesDeviceRowsInsteadOfEmployeeActivationCounts() {
        DeviceOperationalSummaryRow row = new DeviceOperationalSummaryRow();
        row.setTotal(1000);
        row.setOnline(960);
        row.setLowBattery(12);
        row.setDataInterrupted(6);
        row.setFaulted(3);
        when(mapper.getSummary(20, 15)).thenReturn(row);

        DeviceOperationalSummaryView result = service.getSummary();

        assertEquals(1000, result.total());
        assertEquals(960, result.online());
        assertEquals(40, result.offline());
        assertEquals(12, result.lowBattery());
        assertEquals(6, result.dataInterrupted());
        assertEquals(3, result.faulted());
    }

    @Test
    void faultPrecedesTelemetryExceptions() {
        Device device = new Device();
        device.setStatus(1);
        device.setBatteryLevel(5);
        device.setLastOnlineTime(LocalDateTime.now().minusHours(1));
        DeviceOperationalStateView state = new DeviceOperationalStateView(
                1L, "FAULT", "SENSOR", "传感器故障", "ASSIGNED",
                "admin", null, null, "admin", null);

        assertTrue(service.isLowBattery(device));
        assertTrue(service.isDataInterrupted(device));
        assertEquals("FAULT", service.currentAbnormal(device, state));
    }

    @Test
    void zeroBatteryPlaceholderIsUnknownInsteadOfLowBattery() {
        Device device = new Device();
        device.setBatteryLevel(0);
        device.setLastOnlineTime(LocalDateTime.now());

        assertNull(service.validBatteryLevel(device));
        assertFalse(service.isLowBattery(device));
        assertEquals("NORMAL", service.currentAbnormal(device, null, true));
    }
}
